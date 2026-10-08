package com.limoz.fleet.fuel.service;

import com.limoz.fleet.fuel.domain.FuelCalculator;
import com.limoz.fleet.fuel.domain.FuelTransaction;
import com.limoz.fleet.fuel.mapper.FuelMapper;
import com.limoz.fleet.fuel.repository.FuelSpecifications;
import com.limoz.fleet.fuel.repository.FuelTransactionRepository;

import com.limoz.fleet.audit.domain.AuditAction;
import com.limoz.fleet.audit.service.AuditService;
import com.limoz.fleet.common.api.PageResponse;
import com.limoz.fleet.common.event.OperationalEvent;
import com.limoz.fleet.common.event.Severity;
import com.limoz.fleet.common.exception.BusinessRuleException;
import com.limoz.fleet.common.exception.DuplicateResourceException;
import com.limoz.fleet.common.exception.ResourceNotFoundException;
import com.limoz.fleet.common.util.DateRanges;
import com.limoz.fleet.config.CacheConfig;
import com.limoz.fleet.driver.service.DriverService;
import com.limoz.fleet.fuel.dto.FleetFuelSummaryResponse;
import com.limoz.fleet.fuel.dto.FuelFilter;
import com.limoz.fleet.fuel.dto.FuelSummaryResponse;
import com.limoz.fleet.fuel.dto.FuelTransactionRequest;
import com.limoz.fleet.fuel.dto.FuelTransactionResponse;
import com.limoz.fleet.security.Roles;
import com.limoz.fleet.security.SecurityUtils;
import com.limoz.fleet.settings.domain.SettingKeys;
import com.limoz.fleet.settings.service.SettingsService;
import com.limoz.fleet.vehicle.domain.OdometerLog;
import com.limoz.fleet.vehicle.repository.OdometerLogRepository;
import com.limoz.fleet.vehicle.service.OdometerService;
import com.limoz.fleet.vehicle.domain.OdometerSource;
import com.limoz.fleet.vehicle.domain.Vehicle;
import com.limoz.fleet.vehicle.service.VehicleService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * Fuel log. Every transaction derives its distance and consumption from the chronologically previous
 * transaction of the same vehicle, so inserting, editing or archiving one also refreshes the next one.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class FuelService {

    private static final int DEFAULT_SUMMARY_DAYS = 30;

    private final FuelTransactionRepository repository;
    private final VehicleService vehicleService;
    private final DriverService driverService;
    private final OdometerService odometerService;
    private final OdometerLogRepository odometerLogRepository;
    private final SettingsService settingsService;
    private final FuelMapper mapper;
    private final AuditService auditService;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final ZoneId operationalZone;

    // ---------------------------------------------------------------- queries

    @Transactional(readOnly = true)
    public PageResponse<FuelTransactionResponse> search(FuelFilter filter, Pageable pageable) {
        return PageResponse.from(repository.findAll(FuelSpecifications.from(filter, operationalZone), pageable).map(mapper::toResponse));
    }

    @Transactional(readOnly = true)
    public PageResponse<FuelTransactionResponse> forVehicle(Long vehicleId, Pageable pageable) {
        vehicleService.load(vehicleId);
        FuelFilter filter = new FuelFilter(null, vehicleId, null, null, null, null, null, null, null);
        return search(filter, pageable);
    }

    @Transactional(readOnly = true)
    public FuelTransactionResponse get(Long id) {
        return mapper.toResponse(load(id));
    }

    @Transactional(readOnly = true)
    public FuelSummaryResponse vehicleSummary(Long vehicleId, LocalDate from, LocalDate to) {
        Vehicle vehicle = vehicleService.load(vehicleId);
        LocalDate[] range = range(from, to);
        DateRanges.InstantRange r = DateRanges.between(range[0], range[1], operationalZone);
        FuelTransactionRepository.Totals totals = repository.totals(r.from(), r.to(), vehicleId, null);
        return mapper.toSummary(totals, vehicleId, vehicle.getPlateNumber(), vehicle.getDisplayName(), range[0], range[1]);
    }

    @Transactional(readOnly = true)
    public FleetFuelSummaryResponse fleetSummary(LocalDate from, LocalDate to, Long vehicleId, Long categoryId) {
        LocalDate[] range = range(from, to);
        DateRanges.InstantRange r = DateRanges.between(range[0], range[1], operationalZone);
        FuelSummaryResponse totals = mapper.toSummary(repository.totals(r.from(), r.to(), vehicleId, categoryId),
                null, null, null, range[0], range[1]);
        List<FuelSummaryResponse> perVehicle = repository.totalsPerVehicle(r.from(), r.to(), vehicleId, categoryId).stream()
                .map(v -> mapper.toSummary(v, v.getVehicleId(), v.getPlateNumber(),
                        v.getMake() + " " + v.getModel() + " (" + v.getPlateNumber() + ")", range[0], range[1]))
                .toList();
        return new FleetFuelSummaryResponse(totals, perVehicle);
    }

    // ---------------------------------------------------------------- commands

    @CacheEvict(cacheNames = CacheConfig.DASHBOARD, allEntries = true)
    public FuelTransactionResponse create(FuelTransactionRequest request) {
        Vehicle vehicle = vehicleService.loadActive(request.vehicleId());
        FuelTransaction tx = new FuelTransaction();
        tx.setVehicle(vehicle);
        tx.setEnteredByUserId(SecurityUtils.currentUserId().orElse(null));
        apply(tx, request, null);
        tx = repository.saveAndFlush(tx);
        recordOdometer(tx, vehicle);
        refreshFollowing(vehicle.getId(), tx.getTransactionAt(), tx.getId());
        FuelTransactionResponse response = mapper.toResponse(repository.findDetailedById(tx.getId()).orElseThrow());
        auditService.record(AuditAction.CREATE, "FuelTransaction", tx.getId(), vehicle.getPlateNumber(), null, response,
                "Fuel transaction recorded: " + tx.getLitres() + " L at " + tx.getStationName() + " for " + vehicle.getPlateNumber());
        publishAnomaly(tx);
        return response;
    }

    @CacheEvict(cacheNames = CacheConfig.DASHBOARD, allEntries = true)
    public FuelTransactionResponse update(Long id, FuelTransactionRequest request) {
        FuelTransaction tx = load(id);
        if (tx.isArchived()) {
            throw new BusinessRuleException("FUEL_TRANSACTION_ARCHIVED", "Archived fuel transactions cannot be edited");
        }
        FuelTransactionResponse before = mapper.toResponse(tx);
        Instant previousAt = tx.getTransactionAt();
        boolean wasAnomaly = tx.isAnomaly();
        Vehicle vehicle = tx.getVehicle();
        if (!vehicle.getId().equals(request.vehicleId())) {
            throw new BusinessRuleException("FUEL_VEHICLE_IMMUTABLE", "A fuel transaction cannot be moved to another vehicle; archive it and record a new one");
        }
        apply(tx, request, id);
        tx = repository.saveAndFlush(tx);
        recordOdometer(tx, vehicle);
        if (!previousAt.equals(tx.getTransactionAt())) {
            refreshFollowing(vehicle.getId(), previousAt, tx.getId());
        }
        refreshFollowing(vehicle.getId(), tx.getTransactionAt(), tx.getId());
        FuelTransactionResponse after = mapper.toResponse(tx);
        auditService.record(AuditAction.UPDATE, "FuelTransaction", id, vehicle.getPlateNumber(), before, after, "Fuel transaction updated");
        if (!wasAnomaly) {
            publishAnomaly(tx);
        }
        return after;
    }

    @CacheEvict(cacheNames = CacheConfig.DASHBOARD, allEntries = true)
    public void archive(Long id) {
        FuelTransaction tx = load(id);
        if (tx.isArchived()) {
            return;
        }
        FuelTransactionResponse before = mapper.toResponse(tx);
        tx.setArchived(true);
        repository.saveAndFlush(tx);
        refreshFollowing(tx.getVehicle().getId(), tx.getTransactionAt(), tx.getId());
        auditService.record(AuditAction.ARCHIVE, "FuelTransaction", id, tx.getVehicle().getPlateNumber(), before, mapper.toResponse(tx),
                "Fuel transaction archived");
    }

    // ---------------------------------------------------------------- helpers

    private FuelTransaction load(Long id) {
        return repository.findDetailedById(id).orElseThrow(() -> new ResourceNotFoundException("Fuel transaction", id));
    }

    private void apply(FuelTransaction tx, FuelTransactionRequest r, Long excludeId) {
        Vehicle vehicle = tx.getVehicle();
        tx.setDriver(r.driverId() == null ? null : driverService.loadActive(r.driverId()));
        tx.setTripId(r.tripId());
        tx.setBookingSlotId(r.bookingSlotId());
        Instant at = r.transactionAt() == null ? Instant.now(clock) : r.transactionAt();
        if (at.isAfter(Instant.now(clock).plusSeconds(300))) {
            throw new BusinessRuleException("FUEL_DATE_IN_FUTURE", "Fuel transaction date cannot be in the future");
        }
        tx.setTransactionAt(at);
        tx.setStationName(r.stationName().trim());
        tx.setSupplierName(blankToNull(r.supplierName()));
        tx.setFuelType(r.fuelType() == null ? vehicle.getFuelType() : r.fuelType());
        tx.setLitres(r.litres().setScale(2, RoundingMode.HALF_UP));
        tx.setPricePerLitre(r.pricePerLitre().setScale(2, RoundingMode.HALF_UP));
        tx.setCurrency(r.currency() == null || r.currency().isBlank() ? "RWF" : r.currency().trim().toUpperCase());
        tx.setOdometerKm(r.odometerKm());
        tx.setSensorDetectedLitres(r.sensorDetectedLitres());
        tx.setFullTank(r.fullTank() == null || r.fullTank());
        String receipt = blankToNull(r.receiptNumber());
        if (receipt != null && repository.receiptExists(vehicle.getId(), receipt, excludeId)) {
            throw new DuplicateResourceException("Receipt " + receipt + " is already recorded for vehicle " + vehicle.getPlateNumber());
        }
        tx.setReceiptNumber(receipt);
        tx.setReceiptAttachmentId(r.receiptAttachmentId());
        tx.setPaymentMethod(r.paymentMethod());
        tx.setNotes(blankToNull(r.notes()));
        compute(tx, excludeId);
    }

    /** Derives previous odometer, distance, consumption, variance and the anomaly flag from the chronologically previous transaction. */
    private void compute(FuelTransaction tx, Long excludeId) {
        FuelTransaction previous = repository.findPreviousBefore(tx.getVehicle().getId(), tx.getTransactionAt(), excludeId, PageRequest.of(0, 1))
                .stream().findFirst().orElse(null);
        Long previousOdometer = previous == null ? null : previous.getOdometerKm();
        if (previousOdometer != null && tx.getOdometerKm() < previousOdometer) {
            throw new BusinessRuleException("ODOMETER_DECREASE",
                    "Odometer reading " + tx.getOdometerKm() + " km is lower than the previous fuel transaction reading of "
                            + previousOdometer + " km on " + DateRanges.toLocalDate(previous.getTransactionAt(), operationalZone));
        }
        FuelCalculator.Result result = FuelCalculator.compute(tx.getLitres(), tx.getPricePerLitre(), tx.getOdometerKm(), previousOdometer,
                tx.getSensorDetectedLitres(), settingsService.getDecimal(SettingKeys.FUEL_HIGH_CONSUMPTION_L_PER_100KM),
                settingsService.getDecimal(SettingKeys.FUEL_VARIANCE_TOLERANCE_LITRES));
        tx.setPreviousOdometerKm(previousOdometer);
        tx.setTotalAmount(result.totalAmount());
        tx.setDistanceSinceLastKm(result.distanceKm());
        tx.setConsumptionLPer100km(result.consumptionLPer100km());
        tx.setKmPerLitre(result.kmPerLitre());
        tx.setVarianceLitres(result.varianceLitres());
        tx.setAnomaly(result.anomaly());
        tx.setAnomalyReason(result.anomalyReason());
    }

    /**
     * Pushes the reading to the vehicle unless the transaction is back-dated before the vehicle's latest
     * odometer entry; a back-dated transaction keeps its own reading but must not rewind the vehicle.
     */
    private void recordOdometer(FuelTransaction tx, Vehicle vehicle) {
        Instant latestReading = odometerLogRepository.findByVehicleIdOrderByRecordedAtDesc(vehicle.getId(), PageRequest.of(0, 1))
                .stream().findFirst().map(OdometerLog::getRecordedAt).orElse(null);
        boolean backDated = latestReading != null && tx.getTransactionAt().isBefore(latestReading);
        if (backDated) {
            return;
        }
        odometerService.record(vehicle, tx.getOdometerKm(), OdometerSource.FUEL, "FuelTransaction", tx.getId(), tx.getTransactionAt());
    }

    /** Recomputes the distance-based figures of the transaction that follows the given moment. */
    private void refreshFollowing(Long vehicleId, Instant after, Long excludeId) {
        repository.findNextAfter(vehicleId, after, excludeId, PageRequest.of(0, 1)).stream().findFirst().ifPresent(next -> {
            boolean wasAnomaly = next.isAnomaly();
            compute(next, next.getId());
            repository.save(next);
            if (!wasAnomaly) {
                publishAnomaly(next);
            }
        });
    }

    private void publishAnomaly(FuelTransaction tx) {
        if (!tx.isAnomaly()) return;
        String plate = tx.getVehicle().getPlateNumber();
        events.publishEvent(OperationalEvent.of("FUEL_ANOMALY", Severity.WARNING, "Fuel anomaly on " + plate,
                tx.getAnomalyReason() + " (" + tx.getLitres() + " L at " + tx.getStationName() + ")",
                "FuelTransaction", tx.getId(), plate, "/fuel/" + tx.getId(), Roles.FLEET_MANAGER, Roles.FINANCE, Roles.MANAGEMENT));
    }

    private LocalDate[] range(LocalDate from, LocalDate to) {
        LocalDate end = to == null ? LocalDate.now(clock) : to;
        LocalDate start = from == null ? end.minusDays(DEFAULT_SUMMARY_DAYS) : from;
        if (start.isAfter(end)) {
            throw new BusinessRuleException("INVALID_DATE_RANGE", "'from' must not be after 'to'");
        }
        return new LocalDate[]{start, end};
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
