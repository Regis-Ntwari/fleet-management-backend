package com.limoz.fleet.finance;

import com.limoz.fleet.audit.AuditAction;
import com.limoz.fleet.audit.AuditService;
import com.limoz.fleet.common.api.PageResponse;
import com.limoz.fleet.common.exception.BusinessRuleException;
import com.limoz.fleet.common.exception.ResourceNotFoundException;
import com.limoz.fleet.common.sequence.ReferenceNumberService;
import com.limoz.fleet.common.sequence.ReferenceType;
import com.limoz.fleet.common.util.DateRanges;
import com.limoz.fleet.common.util.Specifications;
import com.limoz.fleet.config.CacheConfig;
import com.limoz.fleet.customer.CustomerService;
import com.limoz.fleet.finance.dto.PaymentFilter;
import com.limoz.fleet.finance.dto.PaymentRequest;
import com.limoz.fleet.finance.dto.PaymentResponse;
import com.limoz.fleet.finance.dto.PaymentSummaryResponse;
import com.limoz.fleet.incident.FineStatus;
import com.limoz.fleet.incident.TrafficFine;
import com.limoz.fleet.incident.TrafficFineRepository;
import com.limoz.fleet.security.SecurityUtils;
import com.limoz.fleet.settings.SettingKeys;
import com.limoz.fleet.settings.SettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Payment ledger (IN from clients, OUT to vendors, garages, authorities and staff). A payment may settle
 * one linked record - invoice, maintenance job, expense or traffic fine - whose status is kept in sync
 * here, both when the payment is recorded and when it is reversed.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class PaymentService {

    private final PaymentRepository repository;
    private final InvoiceService invoiceService;
    private final ExpenseRepository expenseRepository;
    private final TrafficFineRepository trafficFineRepository;
    private final MaintenanceLedgerUpdater maintenanceLedgerUpdater;
    private final CustomerService customerService;
    private final FinanceMapper mapper;
    private final ReferenceNumberService referenceNumberService;
    private final SettingsService settingsService;
    private final AuditService auditService;
    private final JdbcClient jdbcClient;
    private final Clock clock;
    private final ZoneId operationalZone;

    @Transactional(readOnly = true)
    public PageResponse<PaymentResponse> search(PaymentFilter f, Pageable pageable) {
        DateRanges.InstantRange range = range(f.from(), f.to());
        Specification<Payment> spec = Specifications.and(
                Boolean.TRUE.equals(f.includeReversed()) ? null : Specifications.isFalse("reversed"),
                Specifications.likeAny(f.q(), "paymentNumber", "counterpartyName", "externalReference"),
                Specifications.equal("direction", f.direction()),
                Specifications.equal("method", f.method()),
                Specifications.equal("customer.id", f.customerId()),
                Specifications.equal("invoice.id", f.invoiceId()),
                Specifications.equal("expense.id", f.expenseId()),
                Specifications.equal("trafficFineId", f.trafficFineId()),
                Specifications.equal("maintenanceRecordId", f.maintenanceRecordId()),
                Specifications.instantBetween("paidAt", range.from(), range.to()));
        return PageResponse.from(repository.findAll(spec, pageable).map(mapper::toResponse));
    }

    @Transactional(readOnly = true)
    public PaymentResponse get(Long id) {
        return mapper.toResponse(load(id));
    }

    @Transactional(readOnly = true)
    public Payment load(Long id) {
        return repository.findDetailedById(id).orElseThrow(() -> new ResourceNotFoundException("Payment", id));
    }

    /** Received / paid out in RWF for the period (defaults to the last 30 days), split by method. */
    @Transactional(readOnly = true)
    public PaymentSummaryResponse summary(LocalDate from, LocalDate to) {
        LocalDate end = to == null ? LocalDate.now(clock) : to;
        LocalDate start = from == null ? end.minusDays(29) : from;
        DateRanges.InstantRange range = DateRanges.between(start, end, operationalZone);
        BigDecimal rate = settingsService.getDecimal(SettingKeys.USD_TO_RWF_RATE);
        Map<PaymentMethod, BigDecimal> in = new EnumMap<>(PaymentMethod.class);
        Map<PaymentMethod, BigDecimal> out = new EnumMap<>(PaymentMethod.class);
        long[] count = {0};
        jdbcClient.sql("select direction, method, count(*) as cnt, "
                        + "coalesce(sum(case when currency = 'USD' then amount * :rate else amount end), 0) as total "
                        + "from payments where reversed = false and paid_at >= :from and paid_at < :to group by direction, method")
                .param("rate", rate)
                .param("from", range.from().atOffset(ZoneOffset.UTC))
                .param("to", range.to().atOffset(ZoneOffset.UTC))
                .query((rs, i) -> {
                    count[0] += rs.getLong("cnt");
                    Map<PaymentMethod, BigDecimal> target = "IN".equals(rs.getString("direction")) ? in : out;
                    target.merge(PaymentMethod.valueOf(rs.getString("method")), rs.getBigDecimal("total"), BigDecimal::add);
                    return null;
                })
                .list();
        BigDecimal received = in.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal paidOut = out.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return new PaymentSummaryResponse(start, end, Currencies.RWF, count[0], received, paidOut, received.subtract(paidOut), in, out);
    }

    @CacheEvict(cacheNames = CacheConfig.DASHBOARD, allEntries = true)
    public PaymentResponse record(PaymentRequest request) {
        long links = Stream.of(request.invoiceId(), request.maintenanceRecordId(), request.expenseId(), request.trafficFineId())
                .filter(id -> id != null).count();
        if (links > 1) {
            throw new BusinessRuleException("PAYMENT_LINK_AMBIGUOUS", "A payment can settle only one record (invoice, maintenance job, expense or fine)");
        }
        Instant paidAt = request.paidAt() == null ? Instant.now(clock) : request.paidAt();
        if (paidAt.isAfter(Instant.now(clock))) {
            throw new BusinessRuleException("PAYMENT_DATE_IN_FUTURE", "Payment date cannot be in the future");
        }
        Payment payment = new Payment();
        payment.setPaymentNumber(referenceNumberService.next(ReferenceType.PAYMENT));
        payment.setDirection(request.direction());
        payment.setMethod(request.method());
        payment.setAmount(request.amount());
        payment.setCurrency(Currencies.normalise(request.currency()));
        payment.setPaidAt(paidAt);
        payment.setExternalReference(request.externalReference());
        payment.setReceiptAttachmentId(request.receiptAttachmentId());
        payment.setNotes(request.notes());
        payment.setRecordedByUserId(SecurityUtils.currentUserId().orElse(null));
        payment.setCounterpartyName(request.counterpartyName() == null || request.counterpartyName().isBlank() ? null : request.counterpartyName().trim());
        if (request.customerId() != null) {
            payment.setCustomer(customerService.load(request.customerId()));
        }

        Invoice invoice = null;
        Expense expense = null;
        TrafficFine fine = null;
        if (request.invoiceId() != null) {
            invoice = linkInvoice(payment, request.invoiceId());
        } else if (request.maintenanceRecordId() != null) {
            linkMaintenance(payment, request.maintenanceRecordId());
        } else if (request.expenseId() != null) {
            expense = linkExpense(payment, request.expenseId());
        } else if (request.trafficFineId() != null) {
            fine = linkFine(payment, request.trafficFineId());
        }
        if (payment.getCounterpartyName() == null) {
            throw new BusinessRuleException("COUNTERPARTY_REQUIRED", "Counterparty name is required for a payment that settles no record");
        }

        payment = repository.saveAndFlush(payment);
        PaymentResponse response = mapper.toResponse(payment);
        auditService.record(AuditAction.CREATE, "Payment", payment.getId(), payment.getPaymentNumber(), null, response,
                (payment.getDirection() == PaymentDirection.IN ? "Received " : "Paid out ") + payment.getAmount() + " " + payment.getCurrency()
                        + (payment.getDirection() == PaymentDirection.IN ? " from " : " to ") + payment.getCounterpartyName());

        if (invoice != null) {
            invoiceService.applyPayments(invoice);
        } else if (payment.getMaintenanceRecordId() != null) {
            maintenanceLedgerUpdater.recomputeAmountPaid(payment.getMaintenanceRecordId());
        } else if (expense != null) {
            settleExpense(expense, payment);
        } else if (fine != null) {
            settleFine(fine, payment);
        }
        return response;
    }

    /** Marks a payment as reversed (never deleted) and undoes what it settled. */
    @CacheEvict(cacheNames = CacheConfig.DASHBOARD, allEntries = true)
    public PaymentResponse reverse(Long id, String reason) {
        Payment payment = load(id);
        if (payment.isReversed()) {
            throw new BusinessRuleException("PAYMENT_ALREADY_REVERSED", "Payment " + payment.getPaymentNumber() + " is already reversed");
        }
        PaymentResponse before = mapper.toResponse(payment);
        payment.setReversed(true);
        payment.setReversalReason(reason);
        payment = repository.saveAndFlush(payment);
        PaymentResponse after = mapper.toResponse(payment);
        auditService.record(AuditAction.CANCEL, "Payment", id, payment.getPaymentNumber(), before, after,
                "Payment " + payment.getPaymentNumber() + " reversed: " + reason);

        if (payment.getInvoice() != null) {
            invoiceService.applyPayments(payment.getInvoice());
        } else if (payment.getMaintenanceRecordId() != null) {
            maintenanceLedgerUpdater.recomputeAmountPaid(payment.getMaintenanceRecordId());
        } else if (payment.getExpense() != null) {
            Expense expense = payment.getExpense();
            if (expense.getStatus() == ExpenseStatus.PAID && !repository.existsByExpenseIdAndReversedFalse(expense.getId())) {
                changeExpenseStatus(expense, ExpenseStatus.APPROVED, "Payment " + payment.getPaymentNumber() + " reversed");
            }
        } else if (payment.getTrafficFineId() != null) {
            TrafficFine fine = trafficFineRepository.findDetailedById(payment.getTrafficFineId()).orElse(null);
            if (fine != null && fine.getStatus() == FineStatus.PAID && id.equals(fine.getPaymentId())) {
                FineStatus from = fine.getStatus();
                fine.setStatus(FineStatus.UNPAID);
                fine.setPaidAt(null);
                fine.setPaymentId(null);
                trafficFineRepository.save(fine);
                auditService.record(AuditAction.STATUS_CHANGE, "TrafficFine", fine.getId(), fine.getFineNumber(),
                        Map.of("status", from), Map.of("status", FineStatus.UNPAID), "Payment " + payment.getPaymentNumber() + " reversed");
            }
        }
        return after;
    }

    // ---------------------------------------------------------------- linked records

    private Invoice linkInvoice(Payment payment, Long invoiceId) {
        Invoice invoice = invoiceService.load(invoiceId);
        requireDirection(payment, PaymentDirection.IN, "an invoice");
        if (!invoice.getStatus().isPayable()) {
            throw new BusinessRuleException("INVOICE_NOT_PAYABLE", "Invoice " + invoice.getInvoiceNumber() + " is " + invoice.getStatus()
                    + " and cannot receive payments");
        }
        requireCurrency(payment, invoice.getCurrency(), "invoice " + invoice.getInvoiceNumber());
        if (payment.getAmount().compareTo(invoice.getBalanceDue()) > 0) {
            throw new BusinessRuleException("OVERPAYMENT", "Amount " + payment.getAmount() + " exceeds the outstanding balance of "
                    + invoice.getBalanceDue() + " " + invoice.getCurrency() + " on invoice " + invoice.getInvoiceNumber());
        }
        if (payment.getCustomer() != null && !payment.getCustomer().getId().equals(invoice.getCustomer().getId())) {
            throw new BusinessRuleException("INVOICE_CUSTOMER_MISMATCH", "Invoice " + invoice.getInvoiceNumber() + " belongs to another client");
        }
        payment.setInvoice(invoice);
        payment.setCustomer(invoice.getCustomer());
        if (payment.getCounterpartyName() == null) {
            payment.setCounterpartyName(invoice.getCustomer().getName());
        }
        return invoice;
    }

    private void linkMaintenance(Payment payment, Long maintenanceRecordId) {
        requireDirection(payment, PaymentDirection.OUT, "a maintenance job");
        if (!repository.maintenanceRecordExists(maintenanceRecordId)) {
            throw new ResourceNotFoundException("Maintenance record", maintenanceRecordId);
        }
        payment.setMaintenanceRecordId(maintenanceRecordId);
    }

    private Expense linkExpense(Payment payment, Long expenseId) {
        Expense expense = expenseRepository.findDetailedById(expenseId).orElseThrow(() -> new ResourceNotFoundException("Expense", expenseId));
        requireDirection(payment, PaymentDirection.OUT, "an expense");
        if (expense.getStatus() != ExpenseStatus.APPROVED) {
            throw new BusinessRuleException("EXPENSE_NOT_APPROVED", "Expense " + expense.getExpenseNumber() + " is " + expense.getStatus()
                    + "; only approved expenses can be paid");
        }
        requireCurrency(payment, expense.getCurrency(), "expense " + expense.getExpenseNumber());
        payment.setExpense(expense);
        if (payment.getCounterpartyName() == null) {
            payment.setCounterpartyName(expense.getSubmittedByName() == null ? "Expense " + expense.getExpenseNumber() : expense.getSubmittedByName());
        }
        return expense;
    }

    private TrafficFine linkFine(Payment payment, Long fineId) {
        TrafficFine fine = trafficFineRepository.findDetailedById(fineId).orElseThrow(() -> new ResourceNotFoundException("Traffic fine", fineId));
        requireDirection(payment, PaymentDirection.OUT, "a traffic fine");
        if (!fine.getStatus().canTransitionTo(FineStatus.PAID)) {
            throw new BusinessRuleException("FINE_NOT_PAYABLE", "Fine " + fine.getFineNumber() + " is " + fine.getStatus());
        }
        requireCurrency(payment, fine.getCurrency(), "fine " + fine.getFineNumber());
        payment.setTrafficFineId(fine.getId());
        if (payment.getCounterpartyName() == null) {
            payment.setCounterpartyName("Rwanda National Police");
        }
        return fine;
    }

    private void settleExpense(Expense expense, Payment payment) {
        changeExpenseStatus(expense, ExpenseStatus.PAID, "Paid with " + payment.getPaymentNumber());
    }

    private void changeExpenseStatus(Expense expense, ExpenseStatus to, String description) {
        ExpenseStatus from = expense.getStatus();
        expense.setStatus(to);
        expenseRepository.save(expense);
        auditService.record(AuditAction.STATUS_CHANGE, "Expense", expense.getId(), expense.getExpenseNumber(),
                Map.of("status", from), Map.of("status", to), description);
    }

    private void settleFine(TrafficFine fine, Payment payment) {
        FineStatus from = fine.getStatus();
        fine.setStatus(FineStatus.PAID);
        fine.setPaidAt(payment.getPaidAt());
        fine.setPaymentId(payment.getId());
        trafficFineRepository.save(fine);
        auditService.record(AuditAction.STATUS_CHANGE, "TrafficFine", fine.getId(), fine.getFineNumber(),
                Map.of("status", from), Map.of("status", FineStatus.PAID), "Fine paid with " + payment.getPaymentNumber());
    }

    private void requireDirection(Payment payment, PaymentDirection expected, String what) {
        if (payment.getDirection() != expected) {
            throw new BusinessRuleException("PAYMENT_DIRECTION_MISMATCH", "A payment settling " + what + " must be " + expected);
        }
    }

    private void requireCurrency(Payment payment, String expected, String what) {
        if (!payment.getCurrency().equals(expected)) {
            throw new BusinessRuleException("CURRENCY_MISMATCH", "Payment currency " + payment.getCurrency() + " does not match " + what
                    + " (" + expected + ")");
        }
    }

    private DateRanges.InstantRange range(LocalDate from, LocalDate to) {
        Instant start = from == null ? null : from.atStartOfDay(operationalZone).toInstant();
        Instant end = to == null ? null : to.plusDays(1).atStartOfDay(operationalZone).toInstant().minusMillis(1);
        return new DateRanges.InstantRange(start, end);
    }
}
