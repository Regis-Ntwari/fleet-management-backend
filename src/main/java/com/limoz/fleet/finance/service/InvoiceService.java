package com.limoz.fleet.finance.service;

import com.limoz.fleet.finance.domain.Currencies;
import com.limoz.fleet.finance.domain.Invoice;
import com.limoz.fleet.finance.domain.InvoiceCalculator;
import com.limoz.fleet.finance.domain.InvoiceLine;
import com.limoz.fleet.finance.domain.InvoiceStatus;
import com.limoz.fleet.finance.domain.Payment;
import com.limoz.fleet.finance.domain.PaymentDirection;
import com.limoz.fleet.finance.domain.PaymentTerms;
import com.limoz.fleet.finance.mapper.FinanceMapper;
import com.limoz.fleet.finance.repository.BookingBillingReader;
import com.limoz.fleet.finance.repository.InvoiceRepository;
import com.limoz.fleet.finance.repository.PaymentRepository;

import com.limoz.fleet.audit.domain.AuditAction;
import com.limoz.fleet.audit.service.AuditService;
import com.limoz.fleet.common.api.PageResponse;
import com.limoz.fleet.common.event.OperationalEvent;
import com.limoz.fleet.common.event.Severity;
import com.limoz.fleet.common.exception.BusinessRuleException;
import com.limoz.fleet.common.exception.DuplicateResourceException;
import com.limoz.fleet.common.exception.InvalidStateTransitionException;
import com.limoz.fleet.common.exception.ResourceNotFoundException;
import com.limoz.fleet.common.sequence.ReferenceNumberService;
import com.limoz.fleet.common.sequence.ReferenceType;
import com.limoz.fleet.common.util.Specifications;
import com.limoz.fleet.config.CacheConfig;
import com.limoz.fleet.customer.domain.Customer;
import com.limoz.fleet.customer.service.CustomerService;
import com.limoz.fleet.customer.domain.PurchaseOrder;
import com.limoz.fleet.customer.service.PurchaseOrderService;
import com.limoz.fleet.finance.dto.InvoiceFilter;
import com.limoz.fleet.finance.dto.InvoiceFromBookingRequest;
import com.limoz.fleet.finance.dto.InvoiceLineRequest;
import com.limoz.fleet.finance.dto.InvoiceRequest;
import com.limoz.fleet.finance.dto.InvoiceResponse;
import com.limoz.fleet.finance.dto.ReadyToBillResponse;
import com.limoz.fleet.security.Roles;
import com.limoz.fleet.settings.domain.SettingKeys;
import com.limoz.fleet.settings.service.SettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Client invoices. Totals are always recomputed from the lines; the status follows the payment ledger
 * (ISSUED -> PARTIALLY_PAID -> PAID, OVERDUE once the due date passes with a balance outstanding).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class InvoiceService {

    private static final Set<String> BILLABLE_BOOKING_STATUSES = Set.of("READY_FOR_BILLING", "COMPLETED");

    private final InvoiceRepository repository;
    private final PaymentRepository paymentRepository;
    private final CustomerService customerService;
    private final PurchaseOrderService purchaseOrderService;
    private final BookingBillingReader bookingReader;
    private final FinanceMapper mapper;
    private final ReferenceNumberService referenceNumberService;
    private final SettingsService settingsService;
    private final AuditService auditService;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    // ---------------------------------------------------------------- queries

    @Transactional(readOnly = true)
    public PageResponse<InvoiceResponse> search(InvoiceFilter f, Pageable pageable) {
        LocalDate today = LocalDate.now(clock);
        Specification<Invoice> spec = Specifications.and(
                Specifications.likeAny(f.q(), "invoiceNumber", "customer.name"),
                Specifications.equal("customer.id", f.customerId()),
                Specifications.equal("bookingId", f.bookingId()),
                Specifications.in("status", f.status()),
                Specifications.dateBetween("issueDate", f.from(), f.to()),
                f.overdue() == null ? null : (root, q, cb) -> {
                    var overdue = cb.or(cb.equal(root.get("status"), InvoiceStatus.OVERDUE),
                            cb.and(root.get("status").in(InvoiceStatus.ISSUED, InvoiceStatus.PARTIALLY_PAID),
                                    cb.lessThan(root.get("dueDate"), today)));
                    return f.overdue() ? overdue : cb.not(overdue);
                });
        return PageResponse.from(repository.findAll(spec, pageable).map(mapper::toSummaryResponse));
    }

    @Transactional(readOnly = true)
    public InvoiceResponse get(Long id) {
        return toDetail(load(id));
    }

    @Transactional(readOnly = true)
    public Invoice load(Long id) {
        return repository.findDetailedById(id).orElseThrow(() -> new ResourceNotFoundException("Invoice", id));
    }

    @Transactional(readOnly = true)
    public List<ReadyToBillResponse> readyToBill() {
        return bookingReader.readyToBill();
    }

    // ---------------------------------------------------------------- commands

    @CacheEvict(cacheNames = CacheConfig.DASHBOARD, allEntries = true)
    public InvoiceResponse create(InvoiceRequest request) {
        Customer customer = customerService.loadActive(request.customerId());
        Invoice invoice = new Invoice();
        invoice.setInvoiceNumber(referenceNumberService.next(ReferenceType.INVOICE));
        invoice.setCustomer(customer);
        applyHeader(invoice, request.bookingId(), request.purchaseOrderId(), request.issueDate(), request.dueDate(), request.paymentTerms(),
                Currencies.normalise(request.currency()), request.discountPercent(), request.notes());
        invoice.replaceLines(toLines(request.lines()));
        recalculate(invoice);
        invoice = repository.save(invoice);
        InvoiceResponse response = toDetail(invoice);
        auditService.record(AuditAction.CREATE, "Invoice", invoice.getId(), invoice.getInvoiceNumber(), null, response,
                "Invoice " + invoice.getInvoiceNumber() + " drafted for " + customer.getName());
        return response;
    }

    /**
     * Generates a draft invoice from a completed booking: one line per deployment voucher (institution
     * amount) when vouchers exist, otherwise one line per requested booking line, plus one line per extra
     * charge. Links the booking and, when exactly one open LPO backs the booking, that LPO.
     */
    @CacheEvict(cacheNames = CacheConfig.DASHBOARD, allEntries = true)
    public InvoiceResponse createFromBooking(Long bookingId, InvoiceFromBookingRequest request) {
        BookingBillingReader.BookingHeader booking = bookingReader.header(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", bookingId));
        if (!BILLABLE_BOOKING_STATUSES.contains(booking.status())) {
            throw new BusinessRuleException("BOOKING_NOT_BILLABLE",
                    "Booking " + booking.bookingNumber() + " is " + booking.status() + "; only READY_FOR_BILLING or COMPLETED bookings can be invoiced");
        }
        if (repository.existsByBookingIdAndStatusNot(bookingId, InvoiceStatus.CANCELLED)) {
            throw new DuplicateResourceException("Booking " + booking.bookingNumber() + " already has an active invoice");
        }
        Customer customer = customerService.loadActive(booking.customerId());
        BigDecimal taxRate = settingsService.getDecimal(SettingKeys.INVOICE_TAX_RATE_PERCENT);
        List<InvoiceLine> lines = new ArrayList<>();
        List<BookingBillingReader.VoucherRow> vouchers = bookingReader.vouchers(bookingId);
        if (!vouchers.isEmpty()) {
            for (BookingBillingReader.VoucherRow v : vouchers) {
                InvoiceLine line = line("Deployment voucher " + v.voucherNumber() + " · " + v.plateNumber(), BigDecimal.ONE,
                        v.institutionAmount(), taxRate, lines.size());
                line.setDeploymentVoucherId(v.id());
                lines.add(line);
            }
        } else {
            for (BookingBillingReader.BookingLineRow bl : bookingReader.lines(bookingId)) {
                BigDecimal quantity = BigDecimal.valueOf(bl.quantity());
                BigDecimal unitPrice = bl.lineTotal().divide(quantity, 2, RoundingMode.HALF_UP);
                String description = bl.category() + " × " + bl.quantity() + " · " + bl.pricingType() + " · "
                        + bl.startDate() + " – " + bl.endDate();
                InvoiceLine line = line(description, quantity, unitPrice, taxRate, lines.size());
                line.setBookingLineId(bl.id());
                lines.add(line);
            }
        }
        for (BookingBillingReader.ExtraChargeRow extra : bookingReader.extraCharges(bookingId)) {
            lines.add(line(extra.description(), BigDecimal.ONE, extra.amount(), taxRate, lines.size()));
        }
        if (lines.isEmpty()) {
            throw new BusinessRuleException("BOOKING_HAS_NO_LINES", "Booking " + booking.bookingNumber() + " has nothing to invoice");
        }
        List<PurchaseOrder> openOrders = purchaseOrderService.openForBooking(bookingId);
        Long purchaseOrderId = openOrders.size() == 1 ? openOrders.get(0).getId() : null;

        Invoice invoice = new Invoice();
        invoice.setInvoiceNumber(referenceNumberService.next(ReferenceType.INVOICE));
        invoice.setCustomer(customer);
        applyHeader(invoice, bookingId, purchaseOrderId, request.issueDate(), null, request.paymentTerms(),
                Currencies.normalise(booking.currency()), request.discountPercent(), request.notes());
        invoice.replaceLines(lines);
        recalculate(invoice);
        invoice = repository.save(invoice);
        InvoiceResponse response = toDetail(invoice);
        auditService.record(AuditAction.CREATE, "Invoice", invoice.getId(), invoice.getInvoiceNumber(), null, response,
                "Invoice " + invoice.getInvoiceNumber() + " generated from booking " + booking.bookingNumber());
        return response;
    }

    public InvoiceResponse update(Long id, InvoiceRequest request) {
        Invoice invoice = load(id);
        if (invoice.getStatus() != InvoiceStatus.DRAFT) {
            throw new BusinessRuleException("INVOICE_NOT_DRAFT", "Only draft invoices can be edited; " + invoice.getInvoiceNumber()
                    + " is " + invoice.getStatus());
        }
        InvoiceResponse before = toDetail(invoice);
        if (!invoice.getCustomer().getId().equals(request.customerId())) {
            invoice.setCustomer(customerService.loadActive(request.customerId()));
        }
        applyHeader(invoice, request.bookingId(), request.purchaseOrderId(), request.issueDate(), request.dueDate(), request.paymentTerms(),
                Currencies.normalise(request.currency()), request.discountPercent(), request.notes());
        invoice.replaceLines(toLines(request.lines()));
        recalculate(invoice);
        InvoiceResponse after = toDetail(repository.save(invoice));
        auditService.record(AuditAction.UPDATE, "Invoice", id, invoice.getInvoiceNumber(), before, after, "Invoice updated");
        return after;
    }

    /** DRAFT -> ISSUED (sent to the client). Moves the backing LPO to PART_INVOICED / INVOICED. */
    @CacheEvict(cacheNames = CacheConfig.DASHBOARD, allEntries = true)
    public InvoiceResponse issue(Long id) {
        Invoice invoice = load(id);
        if (invoice.getStatus() != InvoiceStatus.DRAFT) {
            throw new InvalidStateTransitionException("Invoice", invoice.getStatus(), InvoiceStatus.ISSUED);
        }
        if (invoice.getLines().isEmpty()) {
            throw new BusinessRuleException("INVOICE_HAS_NO_LINES", "Add at least one line before issuing the invoice");
        }
        invoice.setSentAt(Instant.now(clock));
        transition(invoice, InvoiceCalculator.deriveStatus(invoice.getTotalAmount(), invoice.getAmountPaid(), invoice.getDueDate(),
                LocalDate.now(clock)), "Invoice issued to " + invoice.getCustomer().getName());
        repository.save(invoice);
        syncPurchaseOrder(invoice);
        return toDetail(invoice);
    }

    /** Cancels an invoice that has not been paid; a settled invoice (or one with live payments) cannot be cancelled. */
    @CacheEvict(cacheNames = CacheConfig.DASHBOARD, allEntries = true)
    public InvoiceResponse cancel(Long id, String reason) {
        Invoice invoice = load(id);
        if (invoice.getStatus() == InvoiceStatus.PAID) {
            throw new BusinessRuleException("INVOICE_PAID", "Invoice " + invoice.getInvoiceNumber() + " is paid and cannot be cancelled");
        }
        if (invoice.getStatus() == InvoiceStatus.CANCELLED) {
            throw new InvalidStateTransitionException("Invoice", invoice.getStatus(), InvoiceStatus.CANCELLED);
        }
        if (paymentRepository.existsByInvoiceIdAndReversedFalse(id)) {
            throw new BusinessRuleException("INVOICE_HAS_PAYMENTS",
                    "Invoice " + invoice.getInvoiceNumber() + " has recorded payments; reverse them before cancelling");
        }
        transition(invoice, InvoiceStatus.CANCELLED, reason == null ? "Invoice cancelled" : "Invoice cancelled: " + reason);
        repository.save(invoice);
        syncPurchaseOrder(invoice);
        return toDetail(invoice);
    }

    /**
     * Recomputes the paid amount and status from the ledger (non-reversed IN payments). Called by the
     * payment service after every payment or reversal linked to the invoice.
     */
    @CacheEvict(cacheNames = CacheConfig.DASHBOARD, allEntries = true)
    public void applyPayments(Invoice invoice) {
        BigDecimal paid = paymentRepository.sumByInvoice(invoice.getId(), PaymentDirection.IN);
        invoice.setAmountPaid(paid == null ? BigDecimal.ZERO : paid);
        if (invoice.getStatus().isPayable() || invoice.getStatus() == InvoiceStatus.PAID) {
            InvoiceStatus target = InvoiceCalculator.deriveStatus(invoice.getTotalAmount(), invoice.getAmountPaid(), invoice.getDueDate(),
                    LocalDate.now(clock));
            if (target == InvoiceStatus.PAID) {
                if (invoice.getPaidAt() == null) {
                    invoice.setPaidAt(Instant.now(clock));
                }
            } else {
                invoice.setPaidAt(null);
            }
            transition(invoice, target, "Amount paid " + invoice.getAmountPaid() + " of " + invoice.getTotalAmount() + " " + invoice.getCurrency());
        }
        repository.save(invoice);
    }

    /** ISSUED / PARTIALLY_PAID -> OVERDUE once the due date has passed. Returns the number of changes. */
    @CacheEvict(cacheNames = CacheConfig.DASHBOARD, allEntries = true)
    public int refreshStatuses() {
        LocalDate today = LocalDate.now(clock);
        int changed = 0;
        for (Invoice invoice : repository.findByStatusInAndDueDateBefore(EnumSet.of(InvoiceStatus.ISSUED, InvoiceStatus.PARTIALLY_PAID), today)) {
            transition(invoice, InvoiceStatus.OVERDUE, "Payment was due on " + invoice.getDueDate());
            events.publishEvent(OperationalEvent.of("INVOICE_OVERDUE", Severity.WARNING,
                    "Invoice " + invoice.getInvoiceNumber() + " is overdue",
                    invoice.getCustomer().getName() + " owes " + invoice.getBalanceDue() + " " + invoice.getCurrency()
                            + "; payment was due on " + invoice.getDueDate(),
                    "Invoice", invoice.getId(), invoice.getInvoiceNumber(), "/invoices/" + invoice.getId(), Roles.FINANCE, Roles.MANAGEMENT));
            changed++;
        }
        if (changed > 0) {
            log.info("Invoice status refresh: {} invoice(s) became overdue", changed);
        }
        return changed;
    }

    // ---------------------------------------------------------------- helpers

    private void applyHeader(Invoice invoice, Long bookingId, Long purchaseOrderId, LocalDate issueDate, LocalDate dueDate,
                             PaymentTerms terms, String currency, BigDecimal discountPercent, String notes) {
        Long customerId = invoice.getCustomer().getId();
        if (bookingId != null && !repository.bookingBelongsToCustomer(bookingId, customerId)) {
            throw new BusinessRuleException("BOOKING_NOT_FOUND", "Booking " + bookingId + " does not exist for this client");
        }
        PurchaseOrder purchaseOrder = null;
        if (purchaseOrderId != null) {
            purchaseOrder = purchaseOrderService.load(purchaseOrderId);
            if (!purchaseOrder.getCustomer().getId().equals(customerId)) {
                throw new BusinessRuleException("PURCHASE_ORDER_CUSTOMER_MISMATCH", "LPO " + purchaseOrder.getLpoNumber() + " belongs to another client");
            }
            if (!purchaseOrder.getStatus().isInvoiceable()) {
                throw new BusinessRuleException("PURCHASE_ORDER_NOT_OPEN", "LPO " + purchaseOrder.getLpoNumber() + " is " + purchaseOrder.getStatus());
            }
        }
        LocalDate issue = issueDate == null ? LocalDate.now(clock) : issueDate;
        PaymentTerms paymentTerms = terms == null ? PaymentTerms.forDays(settingsService.getInt(SettingKeys.INVOICE_DUE_DAYS)) : terms;
        LocalDate due = dueDate == null ? InvoiceCalculator.dueDate(issue, paymentTerms) : dueDate;
        if (due.isBefore(issue)) {
            throw new BusinessRuleException("INVALID_DATES", "Due date cannot precede the issue date");
        }
        invoice.setBookingId(bookingId);
        invoice.setPurchaseOrder(purchaseOrder);
        invoice.setIssueDate(issue);
        invoice.setPaymentTerms(paymentTerms);
        invoice.setDueDate(due);
        invoice.setCurrency(currency);
        invoice.setDiscountPercent(discountPercent == null ? BigDecimal.ZERO : discountPercent.setScale(2, RoundingMode.HALF_UP));
        invoice.setNotes(notes);
    }

    private List<InvoiceLine> toLines(List<InvoiceLineRequest> requests) {
        BigDecimal defaultTax = settingsService.getDecimal(SettingKeys.INVOICE_TAX_RATE_PERCENT);
        List<InvoiceLine> lines = new ArrayList<>();
        for (InvoiceLineRequest r : requests) {
            lines.add(line(r.description().trim(), r.quantity(), r.unitPrice(), r.taxPercent() == null ? defaultTax : r.taxPercent(), lines.size()));
        }
        return lines;
    }

    private InvoiceLine line(String description, BigDecimal quantity, BigDecimal unitPrice, BigDecimal taxPercent, int sortOrder) {
        InvoiceLine line = new InvoiceLine();
        line.setDescription(description.length() > 255 ? description.substring(0, 255) : description);
        line.setQuantity(quantity.setScale(3, RoundingMode.HALF_UP));
        line.setUnitPrice(unitPrice.setScale(2, RoundingMode.HALF_UP));
        line.setTaxPercent(taxPercent.setScale(2, RoundingMode.HALF_UP));
        line.setLineTotal(InvoiceCalculator.lineTotal(line.getQuantity(), line.getUnitPrice()));
        line.setSortOrder(sortOrder);
        return line;
    }

    private void recalculate(Invoice invoice) {
        List<InvoiceCalculator.LineInput> inputs = invoice.getLines().stream()
                .map(l -> new InvoiceCalculator.LineInput(l.getQuantity(), l.getUnitPrice(), l.getTaxPercent())).toList();
        InvoiceCalculator.Totals totals = InvoiceCalculator.compute(inputs, invoice.getDiscountPercent());
        for (int i = 0; i < invoice.getLines().size(); i++) {
            invoice.getLines().get(i).setLineTotal(totals.lineTotals().get(i));
        }
        invoice.setSubtotal(totals.subtotal());
        invoice.setDiscountAmount(totals.discountAmount());
        invoice.setTaxAmount(totals.taxAmount());
        invoice.setTotalAmount(totals.total());
    }

    private void transition(Invoice invoice, InvoiceStatus to, String description) {
        InvoiceStatus from = invoice.getStatus();
        if (from == to) return;
        invoice.setStatus(to);
        auditService.record(AuditAction.STATUS_CHANGE, "Invoice", invoice.getId(), invoice.getInvoiceNumber(),
                Map.of("status", from), Map.of("status", to), description);
    }

    /** Pushes the cumulative invoiced total (issued, non-cancelled invoices) to the backing LPO. */
    private void syncPurchaseOrder(Invoice invoice) {
        if (invoice.getPurchaseOrder() == null) return;
        BigDecimal invoiced = repository.sumTotalByPurchaseOrder(invoice.getPurchaseOrder().getId(),
                EnumSet.of(InvoiceStatus.ISSUED, InvoiceStatus.PARTIALLY_PAID, InvoiceStatus.PAID, InvoiceStatus.OVERDUE));
        purchaseOrderService.markInvoiced(invoice.getPurchaseOrder().getId(), invoiced);
    }

    private InvoiceResponse toDetail(Invoice invoice) {
        return mapper.toResponse(invoice, invoice.getId() == null ? List.of() : paymentRepository.findByInvoiceIdOrderByPaidAtDesc(invoice.getId()));
    }
}
