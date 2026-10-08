package com.limoz.fleet.customer.service;

import com.limoz.fleet.customer.domain.Commitment;
import com.limoz.fleet.customer.domain.Customer;
import com.limoz.fleet.customer.domain.PurchaseOrder;
import com.limoz.fleet.customer.domain.PurchaseOrderStatus;
import com.limoz.fleet.customer.mapper.ContractMapper;
import com.limoz.fleet.customer.repository.PurchaseOrderRepository;

import com.limoz.fleet.audit.domain.AuditAction;
import com.limoz.fleet.audit.service.AuditService;
import com.limoz.fleet.common.api.PageResponse;
import com.limoz.fleet.common.exception.BusinessRuleException;
import com.limoz.fleet.common.exception.InvalidStateTransitionException;
import com.limoz.fleet.common.exception.ResourceNotFoundException;
import com.limoz.fleet.common.sequence.ReferenceNumberService;
import com.limoz.fleet.common.sequence.ReferenceType;
import com.limoz.fleet.common.util.Specifications;
import com.limoz.fleet.config.CacheConfig;
import com.limoz.fleet.customer.dto.PurchaseOrderFilter;
import com.limoz.fleet.customer.dto.PurchaseOrderRequest;
import com.limoz.fleet.customer.dto.PurchaseOrderResponse;
import com.limoz.fleet.finance.domain.Currencies;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Local purchase orders raised by clients. Status follows invoicing (OPEN -> PART_INVOICED -> INVOICED)
 * or expiry (OPEN / PART_INVOICED -> EXPIRED once the expiry date has passed).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class PurchaseOrderService {

    private final PurchaseOrderRepository repository;
    private final CustomerService customerService;
    private final CommitmentService commitmentService;
    private final ContractMapper mapper;
    private final ReferenceNumberService referenceNumberService;
    private final AuditService auditService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public PageResponse<PurchaseOrderResponse> search(PurchaseOrderFilter f, Pageable pageable) {
        Specification<PurchaseOrder> spec = Specifications.and(
                matches(f.q()),
                Specifications.equal("customer.id", f.customerId()),
                Specifications.equal("commitment.id", f.commitmentId()),
                Specifications.equal("bookingId", f.bookingId()),
                Specifications.in("status", f.status()),
                Specifications.dateBetween("issuedDate", f.from(), f.to()));
        Page<PurchaseOrder> page = repository.findAll(spec, pageable);
        List<PurchaseOrderResponse> content = toResponses(page.getContent());
        return PageResponse.from(page.map(p -> content.get(page.getContent().indexOf(p))));
    }

    @Transactional(readOnly = true)
    public List<PurchaseOrderResponse> listForCustomer(Long customerId) {
        customerService.load(customerId);
        return toResponses(repository.findByCustomerIdOrderByIssuedDateDesc(customerId));
    }

    /** Open (invoiceable) LPOs raised for a booking - used by invoicing to link the backing LPO. */
    @Transactional(readOnly = true)
    public List<PurchaseOrder> openForBooking(Long bookingId) {
        return repository.findByBookingIdAndStatusIn(bookingId, EnumSet.of(PurchaseOrderStatus.OPEN, PurchaseOrderStatus.PART_INVOICED));
    }

    @Transactional(readOnly = true)
    public PurchaseOrderResponse get(Long id) {
        return toResponse(load(id));
    }

    @Transactional(readOnly = true)
    public PurchaseOrder load(Long id) {
        return repository.findDetailedById(id).orElseThrow(() -> new ResourceNotFoundException("Purchase order", id));
    }

    @CacheEvict(cacheNames = CacheConfig.DASHBOARD, allEntries = true)
    public PurchaseOrderResponse create(PurchaseOrderRequest request) {
        Customer customer = customerService.loadActive(request.customerId());
        PurchaseOrder po = new PurchaseOrder();
        po.setLpoNumber(referenceNumberService.next(ReferenceType.PURCHASE_ORDER));
        po.setCustomer(customer);
        apply(po, request);
        po.setStatus(po.getExpiryDate() != null && po.getExpiryDate().isBefore(LocalDate.now(clock))
                ? PurchaseOrderStatus.EXPIRED : PurchaseOrderStatus.OPEN);
        po = repository.save(po);
        PurchaseOrderResponse response = toResponse(po);
        auditService.record(AuditAction.CREATE, "PurchaseOrder", po.getId(), po.getLpoNumber(), null, response,
                "LPO " + po.getLpoNumber() + " raised by " + customer.getName());
        return response;
    }

    public PurchaseOrderResponse update(Long id, PurchaseOrderRequest request) {
        PurchaseOrder po = load(id);
        if (po.getStatus().isFinal() || po.getStatus() == PurchaseOrderStatus.INVOICED) {
            throw new BusinessRuleException("PURCHASE_ORDER_LOCKED", "LPO " + po.getLpoNumber() + " is " + po.getStatus() + " and cannot be edited");
        }
        if (!po.getCustomer().getId().equals(request.customerId())) {
            po.setCustomer(customerService.loadActive(request.customerId()));
        }
        PurchaseOrderResponse before = toResponse(po);
        apply(po, request);
        PurchaseOrderResponse after = toResponse(repository.save(po));
        auditService.record(AuditAction.UPDATE, "PurchaseOrder", id, po.getLpoNumber(), before, after, "LPO updated");
        return after;
    }

    public PurchaseOrderResponse markReceived(Long id, LocalDate receivedDate, Long attachmentId) {
        PurchaseOrder po = load(id);
        if (po.getStatus() == PurchaseOrderStatus.CANCELLED) {
            throw new BusinessRuleException("PURCHASE_ORDER_CANCELLED", "LPO " + po.getLpoNumber() + " is cancelled");
        }
        LocalDate received = receivedDate == null ? LocalDate.now(clock) : receivedDate;
        if (received.isBefore(po.getIssuedDate())) {
            throw new BusinessRuleException("INVALID_DATES", "Received date cannot precede the issue date");
        }
        PurchaseOrderResponse before = toResponse(po);
        po.setReceivedDate(received);
        if (attachmentId != null) {
            po.setAttachmentId(attachmentId);
        }
        PurchaseOrderResponse after = toResponse(repository.save(po));
        auditService.record(AuditAction.UPDATE, "PurchaseOrder", id, po.getLpoNumber(), before, after,
                "Signed LPO " + po.getLpoNumber() + " received on " + received);
        return after;
    }

    @CacheEvict(cacheNames = CacheConfig.DASHBOARD, allEntries = true)
    public PurchaseOrderResponse cancel(Long id, String reason) {
        PurchaseOrder po = load(id);
        if (po.getStatus().isFinal() || po.getStatus() == PurchaseOrderStatus.INVOICED || po.getStatus() == PurchaseOrderStatus.PART_INVOICED) {
            throw new InvalidStateTransitionException("Purchase order", po.getStatus(), PurchaseOrderStatus.CANCELLED);
        }
        transition(po, PurchaseOrderStatus.CANCELLED, reason == null ? "LPO cancelled" : "LPO cancelled: " + reason);
        return toResponse(repository.save(po));
    }

    @CacheEvict(cacheNames = CacheConfig.DASHBOARD, allEntries = true)
    public PurchaseOrderResponse close(Long id, String reason) {
        PurchaseOrder po = load(id);
        if (po.getStatus().isFinal()) {
            throw new InvalidStateTransitionException("Purchase order", po.getStatus(), PurchaseOrderStatus.CLOSED);
        }
        transition(po, PurchaseOrderStatus.CLOSED, reason == null ? "LPO closed" : "LPO closed: " + reason);
        return toResponse(repository.save(po));
    }

    /**
     * Called by invoicing with the cumulative amount invoiced against the LPO: PART_INVOICED while the total
     * is below the LPO value, INVOICED once it reaches it, back to OPEN when every invoice was cancelled.
     * Final LPOs (closed / cancelled) are left untouched.
     */
    @CacheEvict(cacheNames = CacheConfig.DASHBOARD, allEntries = true)
    public void markInvoiced(Long id, BigDecimal invoicedAmount) {
        PurchaseOrder po = load(id);
        if (po.getStatus().isFinal()) {
            return;
        }
        BigDecimal invoiced = invoicedAmount == null ? BigDecimal.ZERO : invoicedAmount;
        PurchaseOrderStatus target;
        if (invoiced.signum() <= 0) {
            target = po.getExpiryDate() != null && po.getExpiryDate().isBefore(LocalDate.now(clock)) ? PurchaseOrderStatus.EXPIRED : PurchaseOrderStatus.OPEN;
        } else if (invoiced.compareTo(po.getValue()) < 0) {
            target = PurchaseOrderStatus.PART_INVOICED;
        } else {
            target = PurchaseOrderStatus.INVOICED;
        }
        transition(po, target, "Invoiced " + invoiced + " " + po.getCurrency() + " of " + po.getValue());
        repository.save(po);
    }

    /** OPEN / PART_INVOICED -> EXPIRED once the expiry date has passed. Returns the number of changes. */
    @CacheEvict(cacheNames = CacheConfig.DASHBOARD, allEntries = true)
    public int refreshStatuses() {
        LocalDate today = LocalDate.now(clock);
        int changed = 0;
        for (PurchaseOrder po : repository.findByStatusIn(EnumSet.of(PurchaseOrderStatus.OPEN, PurchaseOrderStatus.PART_INVOICED))) {
            if (po.getExpiryDate() != null && po.getExpiryDate().isBefore(today)) {
                transition(po, PurchaseOrderStatus.EXPIRED, "LPO expired on " + po.getExpiryDate());
                changed++;
            }
        }
        if (changed > 0) {
            log.info("Purchase order status refresh: {} LPO(s) expired", changed);
        }
        return changed;
    }

    private void transition(PurchaseOrder po, PurchaseOrderStatus to, String description) {
        PurchaseOrderStatus from = po.getStatus();
        if (from == to) return;
        po.setStatus(to);
        auditService.record(AuditAction.STATUS_CHANGE, "PurchaseOrder", po.getId(), po.getLpoNumber(),
                Map.of("status", from), Map.of("status", to), description);
    }

    private void apply(PurchaseOrder po, PurchaseOrderRequest r) {
        if (r.expiryDate() != null && r.expiryDate().isBefore(r.issuedDate())) {
            throw new BusinessRuleException("INVALID_DATES", "Expiry date cannot precede the issue date");
        }
        if (r.receivedDate() != null && r.receivedDate().isBefore(r.issuedDate())) {
            throw new BusinessRuleException("INVALID_DATES", "Received date cannot precede the issue date");
        }
        Commitment commitment = null;
        if (r.commitmentId() != null) {
            commitment = commitmentService.load(r.commitmentId());
            if (!commitment.getCustomer().getId().equals(po.getCustomer().getId())) {
                throw new BusinessRuleException("COMMITMENT_CUSTOMER_MISMATCH",
                        "Commitment " + commitment.getReference() + " belongs to another client");
            }
            if (commitment.getStatus().isFinal()) {
                throw new BusinessRuleException("COMMITMENT_NOT_OPEN", "Commitment " + commitment.getReference() + " is " + commitment.getStatus());
            }
        }
        if (r.bookingId() != null && !repository.bookingBelongsToCustomer(r.bookingId(), po.getCustomer().getId())) {
            throw new BusinessRuleException("BOOKING_NOT_FOUND", "Booking " + r.bookingId() + " does not exist for this client");
        }
        po.setCommitment(commitment);
        po.setBookingId(r.bookingId());
        po.setIssuedDate(r.issuedDate());
        po.setExpiryDate(r.expiryDate());
        po.setReceivedDate(r.receivedDate());
        po.setValue(r.value());
        po.setCurrency(Currencies.normalise(r.currency()));
        po.setAttachmentId(r.attachmentId());
        po.setNotes(r.notes());
    }

    private Specification<PurchaseOrder> matches(String q) {
        if (q == null || q.isBlank()) return null;
        String pattern = "%" + q.trim().toLowerCase() + "%";
        return (root, query, cb) -> {
            Join<Object, Object> commitment = root.join("commitment", JoinType.LEFT);
            return cb.or(
                    cb.like(cb.lower(root.get("lpoNumber")), pattern),
                    cb.like(cb.lower(root.get("customer").get("name")), pattern),
                    cb.like(cb.lower(commitment.get("reference")), pattern),
                    cb.like(cb.lower(commitment.get("title")), pattern));
        };
    }

    private PurchaseOrderResponse toResponse(PurchaseOrder po) {
        return toResponses(List.of(po)).get(0);
    }

    /** Resolves booking numbers in one query for the whole page. */
    private List<PurchaseOrderResponse> toResponses(List<PurchaseOrder> orders) {
        if (orders.isEmpty()) return List.of();
        List<Long> bookingIds = orders.stream().map(PurchaseOrder::getBookingId).filter(id -> id != null).distinct().toList();
        Map<Long, String> bookingNumbers = new HashMap<>();
        if (!bookingIds.isEmpty()) {
            repository.bookingReferences(bookingIds).forEach(b -> bookingNumbers.put(b.getBookingId(), b.getBookingNumber()));
        }
        LocalDate today = LocalDate.now(clock);
        return orders.stream().map(po -> mapper.toResponse(po, bookingNumbers.get(po.getBookingId()), today)).toList();
    }
}
