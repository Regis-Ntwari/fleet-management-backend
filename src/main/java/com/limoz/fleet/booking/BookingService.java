package com.limoz.fleet.booking;

import com.limoz.fleet.audit.AuditAction;
import com.limoz.fleet.audit.AuditService;
import com.limoz.fleet.booking.dto.BookingCancelRequest;
import com.limoz.fleet.booking.dto.BookingCounts;
import com.limoz.fleet.booking.dto.BookingFilter;
import com.limoz.fleet.booking.dto.BookingLineRequest;
import com.limoz.fleet.booking.dto.BookingRequest;
import com.limoz.fleet.booking.dto.BookingResponse;
import com.limoz.fleet.booking.dto.BookingSlotResponse;
import com.limoz.fleet.booking.dto.BookingSummary;
import com.limoz.fleet.booking.dto.BookingTab;
import com.limoz.fleet.booking.dto.ExtraChargeRequest;
import com.limoz.fleet.common.api.PageResponse;
import com.limoz.fleet.common.event.OperationalEvent;
import com.limoz.fleet.common.event.Severity;
import com.limoz.fleet.common.exception.BusinessRuleException;
import com.limoz.fleet.common.exception.InvalidStateTransitionException;
import com.limoz.fleet.common.exception.ResourceNotFoundException;
import com.limoz.fleet.common.sequence.ReferenceNumberService;
import com.limoz.fleet.common.sequence.ReferenceType;
import com.limoz.fleet.config.CacheConfig;
import com.limoz.fleet.customer.Customer;
import com.limoz.fleet.customer.CustomerService;
import com.limoz.fleet.security.Roles;
import com.limoz.fleet.vehicle.VehicleCategory;
import com.limoz.fleet.vehicle.VehicleCategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Booking lifecycle: DRAFT / REQUESTED -> CONFIRMED -> READY_FOR_DEPLOYMENT -> DEPLOYED -> READY_FOR_BILLING -> COMPLETED
 * (CANCELLED from any open status). Lines are priced here; one deployment slot is generated per requested
 * vehicle unit. Slot assignment and departures live in {@link DispatchService}, which calls back
 * {@link #refreshStatusFromSlots} so the booking status always reflects its slots.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class BookingService {

    private final BookingRepository bookingRepository;
    private final BookingSlotRepository slotRepository;
    private final DeploymentVoucherRepository voucherRepository;
    private final CommitmentLookupRepository commitmentLookup;
    private final CustomerService customerService;
    private final VehicleCategoryService categoryService;
    private final BookingMapper mapper;
    private final ReferenceNumberService referenceNumberService;
    private final AuditService auditService;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    // ---------------------------------------------------------------- queries

    @Transactional(readOnly = true)
    public PageResponse<BookingSummary> search(BookingFilter filter, Pageable pageable) {
        Page<Booking> page = bookingRepository.findAll(BookingSpecifications.from(filter), pageable);
        return PageResponse.from(summarise(page));
    }

    @Transactional(readOnly = true)
    public List<BookingSummary> recent(int limit) {
        int size = Math.max(1, Math.min(limit, 50));
        Page<Booking> page = bookingRepository.findAll(BookingSpecifications.from(new BookingFilter(null, null, null, null, null, null, null)),
                PageRequest.of(0, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        return summarise(page).getContent();
    }

    @Transactional(readOnly = true)
    public BookingCounts counts() {
        Map<BookingStatus, Long> byStatus = new EnumMap<>(BookingStatus.class);
        for (BookingStatus status : BookingStatus.values()) {
            byStatus.put(status, 0L);
        }
        bookingRepository.countByStatus().forEach(row -> byStatus.put(row.getStatus(), row.getTotal()));
        long all = byStatus.values().stream().mapToLong(Long::longValue).sum();
        long deployment = BookingTab.DEPLOYMENT.statuses().stream().mapToLong(byStatus::get).sum();
        long billing = BookingTab.BILLING.statuses().stream().mapToLong(byStatus::get).sum();
        return new BookingCounts(all, deployment, billing, byStatus);
    }

    @Transactional(readOnly = true)
    public BookingResponse get(Long id) {
        return toResponse(load(id));
    }

    @Transactional(readOnly = true)
    public List<BookingSlotResponse> slots(Long id) {
        load(id);
        return slotRepository.findByBookingIdOrderBySlotNumberAsc(id).stream().map(mapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public Booking load(Long id) {
        return bookingRepository.findDetailedById(id).orElseThrow(() -> new ResourceNotFoundException("Booking", id));
    }

    // ---------------------------------------------------------------- lifecycle

    @CacheEvict(cacheNames = CacheConfig.DASHBOARD, allEntries = true)
    public BookingResponse create(BookingRequest request) {
        validateLines(request.lines());
        Customer customer = customerService.loadActive(request.customerId());
        validateCommitment(customer, request.commitmentId());
        Instant now = Instant.now(clock);

        Booking booking = new Booking();
        booking.setBookingNumber(referenceNumberService.next(ReferenceType.BOOKING));
        booking.setCustomer(customer);
        apply(booking, request);
        rebuildLines(booking, request.lines());
        boolean confirm = Boolean.TRUE.equals(request.confirm());
        booking.setStatus(confirm ? BookingStatus.CONFIRMED : booking.getSource() == BookingSource.INTERNAL ? BookingStatus.DRAFT : BookingStatus.REQUESTED);
        if (confirm) {
            booking.setConfirmedAt(now);
        }
        booking = bookingRepository.save(booking);
        BookingResponse response = toResponse(booking);
        auditService.record(AuditAction.CREATE, "Booking", booking.getId(), booking.getBookingNumber(), null, response,
                "Booking " + booking.getBookingNumber() + " created for " + customer.getName() + " (" + booking.getStatus() + ")");
        if (confirm) {
            publishConfirmed(booking);
        }
        return response;
    }

    /**
     * Edits an open booking. When the lines change, the unassigned slots are regenerated; a booking with
     * any slot already assigned, deployed or returned must not have its lines changed.
     */
    @CacheEvict(cacheNames = CacheConfig.DASHBOARD, allEntries = true)
    public BookingResponse update(Long id, BookingRequest request) {
        Booking booking = load(id);
        if (!booking.getStatus().isEditable()) {
            throw new BusinessRuleException("BOOKING_NOT_EDITABLE",
                    "Booking " + booking.getBookingNumber() + " is " + booking.getStatus() + " and can no longer be edited");
        }
        validateLines(request.lines());
        Customer customer = customerService.loadActive(request.customerId());
        validateCommitment(customer, request.commitmentId());
        BookingResponse before = toResponse(booking);

        boolean linesChanged = !lineSignatures(booking).equals(requestSignatures(request.lines()));
        if (linesChanged) {
            boolean anyAssigned = booking.getSlots().stream().anyMatch(s -> s.getStatus() != SlotStatus.UNASSIGNED);
            if (anyAssigned) {
                throw new BusinessRuleException("BOOKING_SLOTS_ASSIGNED",
                        "Vehicles are already assigned to booking " + booking.getBookingNumber() + "; unassign them before changing the lines");
            }
            booking.getSlots().clear();
            bookingRepository.flush();
            booking.getLines().clear();
            bookingRepository.flush();
        }
        booking.setCustomer(customer);
        apply(booking, request);
        if (linesChanged) {
            rebuildLines(booking, request.lines());
        } else {
            recomputeTotal(booking);
        }
        BookingResponse after = toResponse(bookingRepository.save(booking));
        auditService.record(AuditAction.UPDATE, "Booking", id, booking.getBookingNumber(), before, after,
                "Booking " + booking.getBookingNumber() + " updated" + (linesChanged ? " (lines regenerated)" : ""));
        return after;
    }

    @CacheEvict(cacheNames = CacheConfig.DASHBOARD, allEntries = true)
    public BookingResponse confirm(Long id) {
        Booking booking = load(id);
        requireTransition(booking, BookingStatus.CONFIRMED);
        BookingResponse before = toResponse(booking);
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setConfirmedAt(Instant.now(clock));
        BookingResponse after = toResponse(bookingRepository.save(booking));
        auditService.record(AuditAction.STATUS_CHANGE, "Booking", id, booking.getBookingNumber(), before, after,
                "Booking " + booking.getBookingNumber() + " confirmed");
        publishConfirmed(booking);
        return after;
    }

    /** Cancels an open booking and releases every held (assigned) slot; impossible while a vehicle is out. */
    @CacheEvict(cacheNames = CacheConfig.DASHBOARD, allEntries = true)
    public BookingResponse cancel(Long id, BookingCancelRequest request) {
        Booking booking = load(id);
        requireTransition(booking, BookingStatus.CANCELLED);
        boolean deployed = booking.getSlots().stream().anyMatch(s -> s.getStatus() == SlotStatus.DEPLOYED);
        if (deployed) {
            throw new BusinessRuleException("BOOKING_HAS_DEPLOYED_VEHICLES",
                    "Booking " + booking.getBookingNumber() + " has vehicles on the road; record their return before cancelling");
        }
        BookingResponse before = toResponse(booking);
        Instant now = Instant.now(clock);
        booking.getSlots().stream()
                .filter(s -> s.getStatus() == SlotStatus.UNASSIGNED || s.getStatus() == SlotStatus.ASSIGNED)
                .forEach(s -> s.setStatus(SlotStatus.CANCELLED));
        booking.setStatus(BookingStatus.CANCELLED);
        booking.setCancelledAt(now);
        booking.setCancellationReason(request.reason());
        BookingResponse after = toResponse(bookingRepository.save(booking));
        auditService.record(AuditAction.CANCEL, "Booking", id, booking.getBookingNumber(), before, after,
                "Booking " + booking.getBookingNumber() + " cancelled: " + request.reason());
        publish("BOOKING_CANCELLED", Severity.WARNING, "Booking " + booking.getBookingNumber() + " cancelled",
                booking.getCustomer().getName() + ": " + request.reason(), booking);
        return after;
    }

    @CacheEvict(cacheNames = CacheConfig.DASHBOARD, allEntries = true)
    public BookingResponse complete(Long id) {
        Booking booking = load(id);
        if (booking.getStatus() != BookingStatus.READY_FOR_BILLING) {
            throw new InvalidStateTransitionException("Booking " + booking.getBookingNumber(), booking.getStatus(), BookingStatus.COMPLETED);
        }
        BookingResponse before = toResponse(booking);
        booking.setStatus(BookingStatus.COMPLETED);
        booking.setCompletedAt(Instant.now(clock));
        BookingResponse after = toResponse(bookingRepository.save(booking));
        auditService.record(AuditAction.COMPLETE, "Booking", id, booking.getBookingNumber(), before, after,
                "Booking " + booking.getBookingNumber() + " completed");
        return after;
    }

    // ---------------------------------------------------------------- extra charges

    public BookingResponse addExtraCharge(Long id, ExtraChargeRequest request) {
        Booking booking = load(id);
        requireOpen(booking);
        BookingResponse before = toResponse(booking);
        BookingExtraCharge charge = new BookingExtraCharge();
        charge.setDescription(request.description().trim());
        charge.setAmount(request.amount());
        booking.addExtraCharge(charge);
        recomputeTotal(booking);
        BookingResponse after = toResponse(bookingRepository.save(booking));
        auditService.record(AuditAction.UPDATE, "Booking", id, booking.getBookingNumber(), before, after,
                "Extra charge added to " + booking.getBookingNumber() + ": " + charge.getDescription() + " " + charge.getAmount());
        return after;
    }

    public BookingResponse removeExtraCharge(Long id, Long chargeId) {
        Booking booking = load(id);
        requireOpen(booking);
        BookingExtraCharge charge = booking.getExtraCharges().stream().filter(c -> c.getId().equals(chargeId)).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Extra charge", chargeId));
        BookingResponse before = toResponse(booking);
        booking.getExtraCharges().remove(charge);
        recomputeTotal(booking);
        BookingResponse after = toResponse(bookingRepository.save(booking));
        auditService.record(AuditAction.UPDATE, "Booking", id, booking.getBookingNumber(), before, after,
                "Extra charge removed from " + booking.getBookingNumber() + ": " + charge.getDescription());
        return after;
    }

    // ---------------------------------------------------------------- status derivation (called by dispatch)

    /**
     * Derives the booking status from its slots: any vehicle out -> DEPLOYED; everything returned ->
     * READY_FOR_BILLING; everything assigned but nothing out yet -> READY_FOR_DEPLOYMENT; otherwise CONFIRMED.
     * Draft, requested, completed and cancelled bookings are left untouched.
     */
    public void refreshStatusFromSlots(Booking booking) {
        BookingStatus current = booking.getStatus();
        if (!current.isDispatchable() && current != BookingStatus.READY_FOR_BILLING) {
            return;
        }
        List<BookingSlot> active = booking.activeSlots();
        if (active.isEmpty()) {
            return;
        }
        boolean anyDeployed = active.stream().anyMatch(s -> s.getStatus() == SlotStatus.DEPLOYED);
        boolean anyReturned = active.stream().anyMatch(s -> s.getStatus() == SlotStatus.RETURNED);
        boolean allReturned = active.stream().allMatch(s -> s.getStatus() == SlotStatus.RETURNED);
        boolean anyUnassigned = active.stream().anyMatch(s -> s.getStatus() == SlotStatus.UNASSIGNED);
        BookingStatus target;
        if (anyDeployed || (anyReturned && !allReturned)) {
            target = BookingStatus.DEPLOYED;
        } else if (allReturned) {
            target = BookingStatus.READY_FOR_BILLING;
        } else if (!anyUnassigned) {
            target = BookingStatus.READY_FOR_DEPLOYMENT;
        } else {
            target = BookingStatus.CONFIRMED;
        }
        if (target == current) {
            return;
        }
        requireTransition(booking, target);
        booking.setStatus(target);
        if (target == BookingStatus.DEPLOYED && booking.getDeployedAt() == null) {
            booking.setDeployedAt(Instant.now(clock));
        }
        auditService.record(AuditAction.STATUS_CHANGE, "Booking", booking.getId(), booking.getBookingNumber(),
                Map.of("status", current), Map.of("status", target),
                "Booking " + booking.getBookingNumber() + " status " + current + " -> " + target);
    }

    // ---------------------------------------------------------------- helpers

    private BookingResponse toResponse(Booking booking) {
        String commitmentReference = commitmentLookup.findCommitment(booking.getCommitmentId())
                .map(CommitmentLookupRepository.CommitmentRef::reference).orElse(null);
        List<BookingSlot> slots = booking.getId() == null ? booking.getSlots() : slotRepository.findByBookingIdOrderBySlotNumberAsc(booking.getId());
        List<DeploymentVoucher> vouchers = booking.getId() == null ? List.of() : voucherRepository.findByBookingIdOrderByVoucherDateAscIdAsc(booking.getId());
        return mapper.toResponse(booking, commitmentReference, slots, vouchers);
    }

    private Page<BookingSummary> summarise(Page<Booking> page) {
        List<Long> ids = page.getContent().stream().map(Booking::getId).toList();
        Map<Long, int[]> counts = new HashMap<>();
        if (!ids.isEmpty()) {
            for (BookingSlotRepository.SlotStatusCount row : slotRepository.countByBookingIds(ids)) {
                if (row.getStatus() == SlotStatus.CANCELLED) continue;
                int[] c = counts.computeIfAbsent(row.getBookingId(), k -> new int[2]);
                c[0] += (int) row.getTotal();
                if (row.getStatus() != SlotStatus.UNASSIGNED) {
                    c[1] += (int) row.getTotal();
                }
            }
        }
        return page.map(b -> {
            int[] c = counts.getOrDefault(b.getId(), new int[2]);
            return mapper.toSummary(b, c[0], c[1]);
        });
    }

    private void validateLines(List<BookingLineRequest> lines) {
        for (BookingLineRequest line : lines) {
            if (line.endDate().isBefore(line.startDate())) {
                throw new BusinessRuleException("INVALID_LINE_DATES", "Line end date " + line.endDate() + " is before its start date " + line.startDate());
            }
        }
    }

    /** A booking may only draw down against a live commitment of the same client. */
    private void validateCommitment(Customer customer, Long commitmentId) {
        if (commitmentId == null) {
            return;
        }
        CommitmentLookupRepository.CommitmentRef commitment = commitmentLookup.findCommitment(commitmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Commitment", commitmentId));
        if (!commitment.customerId().equals(customer.getId())) {
            throw new BusinessRuleException("COMMITMENT_CUSTOMER_MISMATCH",
                    "Commitment " + commitment.reference() + " belongs to another client");
        }
        if (!commitment.isDrawable()) {
            throw new BusinessRuleException("COMMITMENT_NOT_ACTIVE",
                    "Commitment " + commitment.reference() + " is " + commitment.status() + " and cannot be used for new bookings");
        }
    }

    private void apply(Booking b, BookingRequest r) {
        b.setCommitmentId(r.commitmentId());
        b.setContactName(r.contactName());
        b.setContactPhone(r.contactPhone());
        b.setContactEmail(r.contactEmail() == null ? null : r.contactEmail().trim().toLowerCase());
        b.setServiceType(r.serviceType() == null ? ServiceType.CHARTER : r.serviceType());
        b.setPickupLocation(r.pickupLocation());
        b.setDropoffLocation(r.dropoffLocation());
        b.setPickupTime(r.pickupTime());
        b.setReturnTime(r.returnTime());
        b.setPassengers(r.passengers());
        b.setCurrency(r.currency() == null ? "RWF" : r.currency());
        b.setSource(r.source() == null ? BookingSource.INTERNAL : r.source());
        b.setNotes(r.notes());
    }

    /** Prices the lines, derives the booking period and generates one UNASSIGNED slot per requested vehicle. */
    private void rebuildLines(Booking booking, List<BookingLineRequest> requests) {
        int slotNumber = 1;
        for (BookingLineRequest r : requests) {
            VehicleCategory category = categoryService.load(r.categoryId());
            BookingLine line = new BookingLine();
            line.setCategory(category);
            line.setPreferredModel(r.preferredModel() == null || r.preferredModel().isBlank() ? null : r.preferredModel().trim());
            line.setQuantity(r.quantity());
            line.setPricingType(r.pricingType());
            line.setStartDate(r.startDate());
            line.setEndDate(r.endDate());
            line.setUnitPrice(r.unitPrice());
            line.setLineTotal(BookingCalculations.lineTotal(r.quantity(), r.unitPrice(),
                    BookingCalculations.billableUnits(r.pricingType(), r.startDate(), r.endDate())));
            line.setNotes(r.notes());
            booking.addLine(line);
            for (int i = 0; i < r.quantity(); i++) {
                BookingSlot slot = new BookingSlot();
                slot.setLine(line);
                slot.setSlotNumber(slotNumber++);
                slot.setCategory(category);
                slot.setStartDate(r.startDate());
                slot.setEndDate(r.endDate());
                booking.addSlot(slot);
            }
        }
        booking.setStartDate(booking.getLines().stream().map(BookingLine::getStartDate).min(Comparator.naturalOrder()).orElseThrow());
        booking.setEndDate(booking.getLines().stream().map(BookingLine::getEndDate).max(Comparator.naturalOrder()).orElseThrow());
        recomputeTotal(booking);
    }

    private void recomputeTotal(Booking booking) {
        BigDecimal lines = booking.getLines().stream().map(BookingLine::getLineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal extras = booking.getExtraCharges().stream().map(BookingExtraCharge::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        booking.setTotalAmount(lines.add(extras));
    }

    private static List<String> lineSignatures(Booking booking) {
        return booking.getLines().stream().map(l -> signature(l.getCategory().getId(), l.getPreferredModel(), l.getQuantity(),
                l.getPricingType(), l.getStartDate(), l.getEndDate(), l.getUnitPrice(), l.getNotes())).sorted().toList();
    }

    private static List<String> requestSignatures(List<BookingLineRequest> lines) {
        return lines.stream().map(l -> signature(l.categoryId(), l.preferredModel() == null || l.preferredModel().isBlank() ? null : l.preferredModel().trim(),
                l.quantity(), l.pricingType(), l.startDate(), l.endDate(), l.unitPrice(), l.notes())).sorted().toList();
    }

    private static String signature(Long categoryId, String model, int quantity, PricingType type, LocalDate start, LocalDate end,
                                    BigDecimal unitPrice, String notes) {
        return categoryId + "|" + model + "|" + quantity + "|" + type + "|" + start + "|" + end + "|" + unitPrice.stripTrailingZeros().toPlainString() + "|" + notes;
    }

    private void requireOpen(Booking booking) {
        if (booking.getStatus().isClosed()) {
            throw new BusinessRuleException("BOOKING_CLOSED", "Booking " + booking.getBookingNumber() + " is " + booking.getStatus());
        }
    }

    private void requireTransition(Booking booking, BookingStatus to) {
        if (!booking.getStatus().canTransitionTo(to)) {
            throw new InvalidStateTransitionException("Booking " + booking.getBookingNumber(), booking.getStatus(), to);
        }
    }

    private void publishConfirmed(Booking booking) {
        publish("BOOKING_CONFIRMED", Severity.INFO, "Booking " + booking.getBookingNumber() + " confirmed",
                booking.getCustomer().getName() + ", " + booking.getStartDate() + " to " + booking.getEndDate()
                        + ", " + booking.activeSlots().size() + " vehicle(s) to deploy", booking);
    }

    void publish(String type, Severity severity, String title, String message, Booking booking) {
        events.publishEvent(OperationalEvent.of(type, severity, title, message, "Booking", booking.getId(), booking.getBookingNumber(),
                "/bookings/" + booking.getId(), Roles.DISPATCHER, Roles.FLEET_MANAGER, Roles.MANAGEMENT));
    }
}
