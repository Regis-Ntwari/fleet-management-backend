package com.limoz.fleet.customer;

import com.limoz.fleet.audit.AuditAction;
import com.limoz.fleet.audit.AuditService;
import com.limoz.fleet.common.api.PageResponse;
import com.limoz.fleet.common.exception.BusinessRuleException;
import com.limoz.fleet.common.exception.InvalidStateTransitionException;
import com.limoz.fleet.common.exception.ResourceNotFoundException;
import com.limoz.fleet.common.sequence.ReferenceNumberService;
import com.limoz.fleet.common.sequence.ReferenceType;
import com.limoz.fleet.common.util.Specifications;
import com.limoz.fleet.config.CacheConfig;
import com.limoz.fleet.customer.dto.CommitmentFilter;
import com.limoz.fleet.customer.dto.CommitmentRequest;
import com.limoz.fleet.customer.dto.CommitmentResponse;
import com.limoz.fleet.customer.dto.CommitmentSummary;
import com.limoz.fleet.finance.Currencies;
import com.limoz.fleet.settings.SettingKeys;
import com.limoz.fleet.settings.SettingsService;
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
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Framework contracts (commitments). Consumption is derived from the bookings drawn against the
 * commitment; the lifecycle is DRAFT -> ACTIVE -> EXPIRING_SOON -> CLOSED (or CANCELLED).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class CommitmentService {

    private final CommitmentRepository repository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final CustomerService customerService;
    private final ContractMapper mapper;
    private final ReferenceNumberService referenceNumberService;
    private final SettingsService settingsService;
    private final AuditService auditService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public PageResponse<CommitmentResponse> search(CommitmentFilter f, Pageable pageable) {
        Specification<Commitment> spec = Specifications.and(
                Specifications.likeAny(f.q(), "reference", "title", "customer.name"),
                Specifications.equal("customer.id", f.customerId()),
                Specifications.in("status", f.status()));
        Page<Commitment> page = repository.findAll(spec, pageable);
        return PageResponse.from(enrich(page));
    }

    @Transactional(readOnly = true)
    public List<CommitmentResponse> listForCustomer(Long customerId) {
        customerService.load(customerId);
        return enrich(repository.findByCustomerIdOrderByPeriodEndDesc(customerId));
    }

    @Transactional(readOnly = true)
    public List<CommitmentSummary> summaries(Long customerId) {
        Collection<CommitmentStatus> open = EnumSet.of(CommitmentStatus.ACTIVE, CommitmentStatus.EXPIRING_SOON);
        List<Commitment> commitments = customerId == null
                ? repository.findByStatusInOrderByReferenceAsc(open)
                : repository.findByCustomerIdAndStatusInOrderByReferenceAsc(customerId, open);
        return commitments.stream().map(mapper::toSummary).toList();
    }

    @Transactional(readOnly = true)
    public CommitmentResponse get(Long id) {
        return toResponse(load(id));
    }

    @Transactional(readOnly = true)
    public Commitment load(Long id) {
        return repository.findDetailedById(id).orElseThrow(() -> new ResourceNotFoundException("Commitment", id));
    }

    @Transactional(readOnly = true)
    public BigDecimal consumedValue(Long id) {
        BigDecimal consumed = repository.consumedValue(id);
        return consumed == null ? BigDecimal.ZERO : consumed;
    }

    @CacheEvict(cacheNames = CacheConfig.DASHBOARD, allEntries = true)
    public CommitmentResponse create(CommitmentRequest request) {
        Customer customer = customerService.loadActive(request.customerId());
        validatePeriod(request);
        Commitment commitment = new Commitment();
        commitment.setReference(referenceNumberService.next(ReferenceType.COMMITMENT));
        commitment.setCustomer(customer);
        apply(commitment, request);
        commitment = repository.save(commitment);
        CommitmentResponse response = toResponse(commitment);
        auditService.record(AuditAction.CREATE, "Commitment", commitment.getId(), commitment.getReference(), null, response,
                "Commitment " + commitment.getReference() + " created for " + customer.getName());
        return response;
    }

    public CommitmentResponse update(Long id, CommitmentRequest request) {
        Commitment commitment = load(id);
        if (commitment.getStatus().isFinal()) {
            throw new BusinessRuleException("COMMITMENT_FINAL", "Commitment " + commitment.getReference() + " is " + commitment.getStatus()
                    + " and can no longer be edited");
        }
        validatePeriod(request);
        if (!commitment.getCustomer().getId().equals(request.customerId())) {
            commitment.setCustomer(customerService.loadActive(request.customerId()));
        }
        CommitmentResponse before = toResponse(commitment);
        apply(commitment, request);
        CommitmentResponse after = toResponse(repository.save(commitment));
        auditService.record(AuditAction.UPDATE, "Commitment", id, commitment.getReference(), before, after, "Commitment updated");
        return after;
    }

    @CacheEvict(cacheNames = CacheConfig.DASHBOARD, allEntries = true)
    public CommitmentResponse activate(Long id) {
        Commitment commitment = load(id);
        if (commitment.getStatus() != CommitmentStatus.DRAFT) {
            throw new InvalidStateTransitionException("Commitment", commitment.getStatus(), CommitmentStatus.ACTIVE);
        }
        CommitmentStatus target = computeOpenStatus(commitment, LocalDate.now(clock));
        transition(commitment, target, "Commitment activated");
        return toResponse(repository.save(commitment));
    }

    @CacheEvict(cacheNames = CacheConfig.DASHBOARD, allEntries = true)
    public CommitmentResponse close(Long id, String reason) {
        Commitment commitment = load(id);
        if (!commitment.getStatus().isOpen()) {
            throw new InvalidStateTransitionException("Commitment", commitment.getStatus(), CommitmentStatus.CLOSED);
        }
        transition(commitment, CommitmentStatus.CLOSED, reason == null ? "Commitment closed" : "Commitment closed: " + reason);
        return toResponse(repository.save(commitment));
    }

    @CacheEvict(cacheNames = CacheConfig.DASHBOARD, allEntries = true)
    public CommitmentResponse cancel(Long id, String reason) {
        Commitment commitment = load(id);
        if (commitment.getStatus().isFinal()) {
            throw new InvalidStateTransitionException("Commitment", commitment.getStatus(), CommitmentStatus.CANCELLED);
        }
        transition(commitment, CommitmentStatus.CANCELLED, reason == null ? "Commitment cancelled" : "Commitment cancelled: " + reason);
        return toResponse(repository.save(commitment));
    }

    /**
     * ACTIVE -> EXPIRING_SOON when the period ends within {@code documents.expiry_warning_days};
     * ACTIVE / EXPIRING_SOON -> CLOSED once the period has ended. Returns the number of changes.
     */
    @CacheEvict(cacheNames = CacheConfig.DASHBOARD, allEntries = true)
    public int refreshStatuses() {
        LocalDate today = LocalDate.now(clock);
        int changed = 0;
        for (Commitment c : repository.findByStatusIn(EnumSet.of(CommitmentStatus.ACTIVE, CommitmentStatus.EXPIRING_SOON))) {
            CommitmentStatus target = computeOpenStatus(c, today);
            if (target != c.getStatus()) {
                transition(c, target, target == CommitmentStatus.CLOSED ? "Contract period ended on " + c.getPeriodEnd()
                        : "Contract period ends on " + c.getPeriodEnd());
                changed++;
            }
        }
        if (changed > 0) {
            log.info("Commitment status refresh: {} commitment(s) changed status", changed);
        }
        return changed;
    }

    private CommitmentStatus computeOpenStatus(Commitment c, LocalDate today) {
        if (c.getPeriodEnd().isBefore(today)) {
            return CommitmentStatus.CLOSED;
        }
        int warningDays = settingsService.getInt(SettingKeys.DOCUMENT_EXPIRY_WARNING_DAYS);
        if (!c.getPeriodEnd().isAfter(today.plusDays(warningDays))) {
            return CommitmentStatus.EXPIRING_SOON;
        }
        return CommitmentStatus.ACTIVE;
    }

    private void transition(Commitment c, CommitmentStatus to, String description) {
        CommitmentStatus from = c.getStatus();
        if (from == to) return;
        c.setStatus(to);
        auditService.record(AuditAction.STATUS_CHANGE, "Commitment", c.getId(), c.getReference(),
                Map.of("status", from), Map.of("status", to), description);
    }

    private void validatePeriod(CommitmentRequest r) {
        if (r.periodStart().isAfter(r.periodEnd())) {
            throw new BusinessRuleException("INVALID_PERIOD", "Period start cannot be after period end");
        }
    }

    private void apply(Commitment c, CommitmentRequest r) {
        c.setTitle(r.title().trim());
        c.setPeriodStart(r.periodStart());
        c.setPeriodEnd(r.periodEnd());
        c.setContractedValue(r.contractedValue());
        c.setCurrency(Currencies.normalise(r.currency()));
        c.setAttachmentId(r.attachmentId());
        c.setNotes(r.notes());
    }

    private CommitmentResponse toResponse(Commitment c) {
        return mapper.toResponse(c, consumedValue(c.getId()),
                purchaseOrderRepository.countByCommitment(c.getId(), PurchaseOrderStatus.CANCELLED));
    }

    private Page<CommitmentResponse> enrich(Page<Commitment> page) {
        List<CommitmentResponse> content = enrich(page.getContent());
        return page.map(c -> content.get(page.getContent().indexOf(c)));
    }

    /** Batch-loads consumed values and LPO counts so list pages do not issue one query per row. */
    private List<CommitmentResponse> enrich(List<Commitment> commitments) {
        if (commitments.isEmpty()) return List.of();
        List<Long> ids = commitments.stream().map(Commitment::getId).toList();
        Map<Long, BigDecimal> consumed = new HashMap<>();
        repository.consumedValues(ids).forEach(v -> consumed.put(v.getCommitmentId(), v.getConsumed()));
        Map<Long, Long> lpoCounts = new HashMap<>();
        purchaseOrderRepository.countByCommitmentIds(ids, PurchaseOrderStatus.CANCELLED).forEach(c -> lpoCounts.put(c.getCommitmentId(), c.getTotal()));
        return commitments.stream()
                .map(c -> mapper.toResponse(c, consumed.getOrDefault(c.getId(), BigDecimal.ZERO), lpoCounts.getOrDefault(c.getId(), 0L)))
                .toList();
    }
}
