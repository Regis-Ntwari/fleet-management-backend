package com.limoz.fleet.finance;

import com.limoz.fleet.audit.AuditAction;
import com.limoz.fleet.audit.AuditService;
import com.limoz.fleet.common.api.PageResponse;
import com.limoz.fleet.common.event.OperationalEvent;
import com.limoz.fleet.common.event.Severity;
import com.limoz.fleet.common.exception.BusinessRuleException;
import com.limoz.fleet.common.exception.InvalidStateTransitionException;
import com.limoz.fleet.common.exception.ResourceNotFoundException;
import com.limoz.fleet.common.sequence.ReferenceNumberService;
import com.limoz.fleet.common.sequence.ReferenceType;
import com.limoz.fleet.common.util.Specifications;
import com.limoz.fleet.config.CacheConfig;
import com.limoz.fleet.driver.DriverService;
import com.limoz.fleet.finance.dto.ExpenseFilter;
import com.limoz.fleet.finance.dto.ExpenseRequest;
import com.limoz.fleet.finance.dto.ExpenseResponse;
import com.limoz.fleet.finance.dto.ExpenseSummaryResponse;
import com.limoz.fleet.finance.dto.FinanceSummaryResponse;
import com.limoz.fleet.finance.dto.PaymentRequest;
import com.limoz.fleet.finance.dto.SettlementRequest;
import com.limoz.fleet.security.AuthenticatedUser;
import com.limoz.fleet.security.Permissions;
import com.limoz.fleet.security.Roles;
import com.limoz.fleet.security.SecurityUtils;
import com.limoz.fleet.settings.SettingKeys;
import com.limoz.fleet.settings.SettingsService;
import com.limoz.fleet.vehicle.VehicleService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Operational expenses with an approval workflow: PENDING -> APPROVED -> PAID, or PENDING -> REJECTED. */
@Service
@RequiredArgsConstructor
@Transactional
public class ExpenseService {

    private static final String AMOUNT_RWF = "coalesce(sum(case when e.currency = 'USD' then e.amount * :rate else e.amount end), 0)";

    private final ExpenseRepository repository;
    private final ExpenseCategoryService categoryService;
    private final PaymentService paymentService;
    private final VehicleService vehicleService;
    private final DriverService driverService;
    private final FinanceMapper mapper;
    private final ReferenceNumberService referenceNumberService;
    private final SettingsService settingsService;
    private final AuditService auditService;
    private final ApplicationEventPublisher events;
    private final JdbcClient jdbcClient;
    private final Clock clock;

    @Transactional(readOnly = true)
    public PageResponse<ExpenseResponse> search(ExpenseFilter f, Pageable pageable) {
        Specification<Expense> spec = Specifications.and(
                Specifications.likeAny(f.q(), "expenseNumber", "description", "category.name", "submittedByName"),
                Specifications.equal("category.id", f.categoryId()),
                Specifications.in("status", f.status()),
                Specifications.equal("vehicle.id", f.vehicleId()),
                Specifications.equal("driver.id", f.driverId()),
                Specifications.equal("submittedByUserId", f.submittedByUserId()),
                Specifications.dateBetween("incurredOn", f.from(), f.to()));
        return PageResponse.from(repository.findAll(spec, pageable).map(mapper::toResponse));
    }

    @Transactional(readOnly = true)
    public ExpenseResponse get(Long id) {
        return mapper.toResponse(load(id));
    }

    @Transactional(readOnly = true)
    public Expense load(Long id) {
        return repository.findDetailedById(id).orElseThrow(() -> new ResourceNotFoundException("Expense", id));
    }

    @CacheEvict(cacheNames = CacheConfig.DASHBOARD, allEntries = true)
    public ExpenseResponse submit(ExpenseRequest request) {
        AuthenticatedUser user = SecurityUtils.requireCurrentUser();
        Expense expense = new Expense();
        expense.setExpenseNumber(referenceNumberService.next(ReferenceType.EXPENSE));
        expense.setSubmittedByUserId(user.id());
        expense.setSubmittedByName(user.fullName());
        apply(expense, request);
        expense = repository.save(expense);
        ExpenseResponse response = mapper.toResponse(expense);
        auditService.record(AuditAction.CREATE, "Expense", expense.getId(), expense.getExpenseNumber(), null, response,
                "Expense " + expense.getExpenseNumber() + " submitted by " + user.fullName());
        events.publishEvent(OperationalEvent.of("EXPENSE_SUBMITTED", Severity.INFO,
                "Expense " + expense.getExpenseNumber() + " awaiting approval",
                user.fullName() + " submitted " + expense.getAmount() + " " + expense.getCurrency() + " for " + expense.getDescription(),
                "Expense", expense.getId(), expense.getExpenseNumber(), "/expenses/" + expense.getId(), Roles.FINANCE));
        return response;
    }

    /** Only pending expenses can be edited, and only by the submitter or a finance manager. */
    public ExpenseResponse update(Long id, ExpenseRequest request) {
        Expense expense = load(id);
        if (expense.getStatus() != ExpenseStatus.PENDING) {
            throw new BusinessRuleException("EXPENSE_NOT_PENDING", "Expense " + expense.getExpenseNumber() + " is " + expense.getStatus()
                    + " and can no longer be edited");
        }
        AuthenticatedUser user = SecurityUtils.requireCurrentUser();
        boolean submitter = expense.getSubmittedByUserId() != null && expense.getSubmittedByUserId().equals(user.id());
        if (!submitter && !user.hasPermission(Permissions.FINANCE_MANAGE)) {
            throw new AccessDeniedException("Only the submitter or finance can edit this expense");
        }
        ExpenseResponse before = mapper.toResponse(expense);
        apply(expense, request);
        ExpenseResponse after = mapper.toResponse(repository.save(expense));
        auditService.record(AuditAction.UPDATE, "Expense", id, expense.getExpenseNumber(), before, after, "Expense updated");
        return after;
    }

    @CacheEvict(cacheNames = CacheConfig.DASHBOARD, allEntries = true)
    public ExpenseResponse approve(Long id) {
        Expense expense = load(id);
        if (expense.getStatus() != ExpenseStatus.PENDING) {
            throw new InvalidStateTransitionException("Expense", expense.getStatus(), ExpenseStatus.APPROVED);
        }
        AuthenticatedUser user = SecurityUtils.requireCurrentUser();
        expense.setApprovedByUserId(user.id());
        expense.setApprovedAt(Instant.now(clock));
        expense.setRejectionReason(null);
        transition(expense, ExpenseStatus.APPROVED, AuditAction.APPROVE, "Expense approved by " + user.fullName());
        return mapper.toResponse(repository.save(expense));
    }

    @CacheEvict(cacheNames = CacheConfig.DASHBOARD, allEntries = true)
    public ExpenseResponse reject(Long id, String reason) {
        Expense expense = load(id);
        if (expense.getStatus() != ExpenseStatus.PENDING) {
            throw new InvalidStateTransitionException("Expense", expense.getStatus(), ExpenseStatus.REJECTED);
        }
        expense.setRejectionReason(reason);
        transition(expense, ExpenseStatus.REJECTED, AuditAction.REJECT, "Expense rejected: " + reason);
        return mapper.toResponse(repository.save(expense));
    }

    /** APPROVED -> PAID by recording an OUT payment in the ledger (the ledger marks the expense paid). */
    @CacheEvict(cacheNames = CacheConfig.DASHBOARD, allEntries = true)
    public ExpenseResponse markPaid(Long id, SettlementRequest request) {
        Expense expense = load(id);
        if (expense.getStatus() != ExpenseStatus.APPROVED) {
            throw new InvalidStateTransitionException("Expense", expense.getStatus(), ExpenseStatus.PAID);
        }
        paymentService.record(new PaymentRequest(PaymentDirection.OUT, request.counterpartyName(), null, null, null, expense.getId(), null,
                request.method(), expense.getAmount(), expense.getCurrency(), request.paidAt(), request.externalReference(),
                request.receiptAttachmentId(), request.notes()));
        return mapper.toResponse(load(id));
    }

    /** Totals by status, category and vehicle (RWF) for expenses incurred in the period (default: last 30 days). */
    @Transactional(readOnly = true)
    public ExpenseSummaryResponse summary(LocalDate from, LocalDate to) {
        LocalDate end = to == null ? LocalDate.now(clock) : to;
        LocalDate start = from == null ? end.minusDays(29) : from;
        BigDecimal rate = settingsService.getDecimal(SettingKeys.USD_TO_RWF_RATE);
        Map<ExpenseStatus, Long> countByStatus = new EnumMap<>(ExpenseStatus.class);
        Map<ExpenseStatus, BigDecimal> amountByStatus = new EnumMap<>(ExpenseStatus.class);
        jdbcClient.sql("select e.status, count(*) as cnt, " + AMOUNT_RWF + " as total from expenses e "
                        + "where e.incurred_on between :from and :to group by e.status")
                .param("rate", rate).param("from", start).param("to", end)
                .query((rs, i) -> {
                    ExpenseStatus status = ExpenseStatus.valueOf(rs.getString("status"));
                    countByStatus.put(status, rs.getLong("cnt"));
                    amountByStatus.put(status, rs.getBigDecimal("total"));
                    return status;
                }).list();
        List<FinanceSummaryResponse.CategoryAmount> byCategory = jdbcClient.sql(
                        "select c.id, c.code, c.name, count(*) as cnt, " + AMOUNT_RWF + " as total from expenses e "
                        + "join expense_categories c on c.id = e.category_id where e.incurred_on between :from and :to and e.status <> 'REJECTED' "
                        + "group by c.id, c.code, c.name order by total desc")
                .param("rate", rate).param("from", start).param("to", end)
                .query((rs, i) -> new FinanceSummaryResponse.CategoryAmount(rs.getLong("id"), rs.getString("code"), rs.getString("name"),
                        rs.getLong("cnt"), rs.getBigDecimal("total")))
                .list();
        List<ExpenseSummaryResponse.VehicleAmount> byVehicle = jdbcClient.sql(
                        "select v.id, v.plate_number, count(*) as cnt, " + AMOUNT_RWF + " as total from expenses e "
                        + "join vehicles v on v.id = e.vehicle_id where e.incurred_on between :from and :to and e.status <> 'REJECTED' "
                        + "group by v.id, v.plate_number order by total desc")
                .param("rate", rate).param("from", start).param("to", end)
                .query((rs, i) -> new ExpenseSummaryResponse.VehicleAmount(rs.getLong("id"), rs.getString("plate_number"), rs.getLong("cnt"),
                        rs.getBigDecimal("total")))
                .list();
        long total = countByStatus.values().stream().mapToLong(Long::longValue).sum();
        BigDecimal totalAmount = amountByStatus.entrySet().stream().filter(e -> e.getKey() != ExpenseStatus.REJECTED)
                .map(Map.Entry::getValue).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new ExpenseSummaryResponse(start, end, Currencies.RWF, total, totalAmount, countByStatus, amountByStatus, byCategory, byVehicle);
    }

    private void transition(Expense expense, ExpenseStatus to, AuditAction action, String description) {
        ExpenseStatus from = expense.getStatus();
        expense.setStatus(to);
        auditService.record(action, "Expense", expense.getId(), expense.getExpenseNumber(),
                Map.of("status", from), Map.of("status", to), description);
    }

    private void apply(Expense e, ExpenseRequest r) {
        if (r.incurredOn().isAfter(LocalDate.now(clock))) {
            throw new BusinessRuleException("EXPENSE_DATE_IN_FUTURE", "Expense date cannot be in the future");
        }
        if (r.tripId() != null && !repository.tripExists(r.tripId())) {
            throw new ResourceNotFoundException("Trip", r.tripId());
        }
        if (r.bookingId() != null && !repository.bookingExists(r.bookingId())) {
            throw new ResourceNotFoundException("Booking", r.bookingId());
        }
        e.setCategory(categoryService.loadActive(r.categoryId()));
        e.setDescription(r.description().trim());
        e.setAmount(r.amount());
        e.setCurrency(Currencies.normalise(r.currency()));
        e.setIncurredOn(r.incurredOn());
        e.setVehicle(r.vehicleId() == null ? null : vehicleService.loadActive(r.vehicleId()));
        e.setDriver(r.driverId() == null ? null : driverService.loadActive(r.driverId()));
        e.setTripId(r.tripId());
        e.setBookingId(r.bookingId());
        e.setReceiptAttachmentId(r.receiptAttachmentId());
        e.setNotes(r.notes());
    }
}
