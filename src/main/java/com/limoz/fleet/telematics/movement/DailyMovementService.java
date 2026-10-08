package com.limoz.fleet.telematics.movement;

import com.limoz.fleet.audit.AuditAction;
import com.limoz.fleet.audit.AuditService;
import com.limoz.fleet.common.api.PageResponse;
import com.limoz.fleet.common.exception.BusinessRuleException;
import com.limoz.fleet.common.util.DateRanges;
import com.limoz.fleet.config.CacheConfig;
import com.limoz.fleet.settings.SettingKeys;
import com.limoz.fleet.settings.SettingsService;
import com.limoz.fleet.telematics.TelematicsDevice;
import com.limoz.fleet.telematics.TelematicsDeviceRepository;
import com.limoz.fleet.telematics.VehiclePositionRepository;
import com.limoz.fleet.telematics.movement.dto.DailyMovementFilter;
import com.limoz.fleet.telematics.movement.dto.DailyMovementResponse;
import com.limoz.fleet.telematics.movement.dto.MovementSummaryResponse;
import com.limoz.fleet.telematics.movement.dto.RecomputeResponse;
import com.limoz.fleet.vehicle.Vehicle;
import com.limoz.fleet.vehicle.VehicleMapper;
import com.limoz.fleet.vehicle.VehicleRepository;
import com.limoz.fleet.vehicle.VehicleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Daily movement analysis: one summary per vehicle per operational day, computed from GPS positions when present and
 * from trip records otherwise. Thresholds come from the {@code movement.*} settings.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class DailyMovementService {

    /** Upper bound on positions read per vehicle and day (one sample every 10 s for 24 h is 8640). */
    static final int MAX_SAMPLES_PER_DAY = 20_000;
    static final int MAX_SERIES_DAYS = 366;

    private final DailyMovementSummaryRepository summaryRepository;
    private final VehiclePositionRepository positionRepository;
    private final TelematicsDeviceRepository deviceRepository;
    private final TripMovementQuery tripQuery;
    private final VehicleRepository vehicleRepository;
    private final VehicleService vehicleService;
    private final VehicleMapper vehicleMapper;
    private final SettingsService settingsService;
    private final AuditService auditService;
    private final Clock clock;
    private final ZoneId operationalZone;

    // ---------------------------------------------------------------- computation

    /** Computes (or recomputes) the summaries of every non-archived vehicle for the given day. */
    @CacheEvict(cacheNames = CacheConfig.DASHBOARD, allEntries = true)
    public RecomputeResponse computeFor(LocalDate date) {
        LocalDate today = LocalDate.now(clock);
        if (date.isAfter(today)) {
            throw new BusinessRuleException("FUTURE_DATE", "Movement cannot be analysed for a future date");
        }
        DateRanges.InstantRange window = DateRanges.forDate(date, operationalZone);
        MovementThresholds thresholds = thresholds();
        Instant computedAt = Instant.now(clock);
        Map<Long, TelematicsDevice> devices = deviceRepository.findAllWithVehicle().stream()
                .collect(Collectors.toMap(d -> d.getVehicle().getId(), Function.identity(), (a, b) -> a));
        Map<Long, List<TripInterval>> trips = tripQuery.tripsStartedBetween(window.from(), window.to());

        int withPositions = 0;
        int withTripsOnly = 0;
        int withoutData = 0;
        List<Vehicle> vehicles = vehicleRepository.findByArchivedFalseOrderByPlateNumberAsc();
        for (Vehicle vehicle : vehicles) {
            TelematicsDevice device = devices.get(vehicle.getId());
            boolean activeDevice = device != null && device.isActive();
            List<MovementSample> samples = positionRepository
                    .findInWindow(vehicle.getId(), window.from(), window.to(), PageRequest.of(0, MAX_SAMPLES_PER_DAY))
                    .stream().map(MovementSample::from).toList();
            List<TripInterval> vehicleTrips = trips.getOrDefault(vehicle.getId(), List.of());

            MovementResult result;
            MovementDataSource source;
            if (!samples.isEmpty()) {
                result = DailyMovementCalculator.fromPositions(samples, date, operationalZone, thresholds, activeDevice);
                if (vehicleTrips.isEmpty()) {
                    source = MovementDataSource.TELEMATICS;
                } else {
                    result = result.withTripsCount(vehicleTrips.size());
                    source = MovementDataSource.MIXED;
                }
                withPositions++;
            } else if (!vehicleTrips.isEmpty()) {
                result = DailyMovementCalculator.fromTrips(vehicleTrips, date, operationalZone, thresholds, activeDevice);
                source = MovementDataSource.TRIPS;
                withTripsOnly++;
            } else {
                result = DailyMovementCalculator.fromPositions(List.of(), date, operationalZone, thresholds, activeDevice);
                source = MovementDataSource.NONE;
                withoutData++;
            }
            upsert(vehicle, date, result, source, computedAt);
        }
        log.info("Daily movement for {}: {} vehicle(s) - {} from positions, {} from trips, {} without data",
                date, vehicles.size(), withPositions, withTripsOnly, withoutData);
        return new RecomputeResponse(date, vehicles.size(), withPositions, withTripsOnly, withoutData);
    }

    /** Manual recomputation (audited); the nightly job calls {@link #computeFor(LocalDate)} directly. */
    public RecomputeResponse recompute(LocalDate date) {
        RecomputeResponse response = computeFor(date);
        auditService.record(AuditAction.SYSTEM, "DailyMovementSummary", null, date.toString(), null, response,
                "Daily movement recomputed for " + date + " (" + response.vehicles() + " vehicles)");
        return response;
    }

    private void upsert(Vehicle vehicle, LocalDate date, MovementResult r, MovementDataSource source, Instant computedAt) {
        DailyMovementSummary s = summaryRepository.findByVehicleIdAndSummaryDate(vehicle.getId(), date).orElseGet(DailyMovementSummary::new);
        s.setVehicle(vehicle);
        s.setSummaryDate(date);
        s.setDistanceKm(r.distanceKm());
        s.setMoved(r.moved());
        s.setFirstMovementAt(r.firstMovementAt());
        s.setLastMovementAt(r.lastMovementAt());
        s.setDrivingMinutes(r.drivingMinutes());
        s.setIdleMinutes(r.idleMinutes());
        s.setNightDrivingMinutes(r.nightDrivingMinutes());
        s.setMaxSpeedKph(r.maxSpeedKph());
        s.setTripsCount(r.tripsCount());
        s.setStartLatitude(r.startLatitude());
        s.setStartLongitude(r.startLongitude());
        s.setEndLatitude(r.endLatitude());
        s.setEndLongitude(r.endLongitude());
        s.setGpsIssue(r.gpsIssue());
        s.setFlags(r.flagNames());
        s.setDataSource(source);
        s.setComputedAt(computedAt);
        summaryRepository.save(s);
    }

    public MovementThresholds thresholds() {
        return new MovementThresholds(
                settingsService.getTime(SettingKeys.NIGHT_DRIVING_START),
                settingsService.getTime(SettingKeys.NIGHT_DRIVING_END),
                settingsService.getInt(SettingKeys.EXCESSIVE_DRIVING_HOURS),
                settingsService.getInt(SettingKeys.HIGH_DAILY_DISTANCE_KM),
                settingsService.getInt(SettingKeys.SPEED_LIMIT_KPH));
    }

    // ---------------------------------------------------------------- queries

    @Transactional(readOnly = true)
    public PageResponse<DailyMovementResponse> search(DailyMovementFilter filter, Pageable pageable) {
        LocalDate yesterday = LocalDate.now(clock).minusDays(1);
        return PageResponse.from(summaryRepository.findAll(DailyMovementSpecifications.from(filter, yesterday), pageable).map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public List<DailyMovementResponse> series(Long vehicleId, LocalDate from, LocalDate to) {
        vehicleService.load(vehicleId);
        LocalDate end = to == null ? LocalDate.now(clock).minusDays(1) : to;
        LocalDate start = from == null ? end.minusDays(29) : from;
        if (start.isAfter(end)) {
            throw new BusinessRuleException("INVALID_RANGE", "'from' must not be after 'to'");
        }
        if (ChronoUnit.DAYS.between(start, end) >= MAX_SERIES_DAYS) {
            throw new BusinessRuleException("RANGE_TOO_LARGE", "Movement series can cover at most one year");
        }
        return summaryRepository.findByVehicleIdAndSummaryDateBetweenOrderBySummaryDateAsc(vehicleId, start, end)
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheConfig.DASHBOARD, key = "'movementSummary:' + #date")
    public MovementSummaryResponse summary(LocalDate date) {
        List<DailyMovementSummary> rows = summaryRepository.findByDate(date);
        int moved = 0;
        int gpsIssues = 0;
        BigDecimal distance = BigDecimal.ZERO;
        long driving = 0;
        long idle = 0;
        int trips = 0;
        Map<MovementFlag, Long> flagged = new EnumMap<>(MovementFlag.class);
        for (MovementFlag flag : MovementFlag.values()) {
            flagged.put(flag, 0L);
        }
        for (DailyMovementSummary s : rows) {
            if (s.isMoved()) moved++;
            if (s.isGpsIssue()) gpsIssues++;
            distance = distance.add(s.getDistanceKm());
            driving += s.getDrivingMinutes();
            idle += s.getIdleMinutes();
            trips += s.getTripsCount();
            for (MovementFlag flag : s.movementFlags()) {
                flagged.merge(flag, 1L, Long::sum);
            }
        }
        return new MovementSummaryResponse(date, rows.size(), moved, rows.size() - moved, gpsIssues, distance, driving, idle, trips, flagged);
    }

    public DailyMovementResponse toResponse(DailyMovementSummary s) {
        return new DailyMovementResponse(s.getId(), vehicleMapper.toSummary(s.getVehicle()), s.getSummaryDate(), s.getDistanceKm(), s.isMoved(),
                s.getFirstMovementAt(), s.getLastMovementAt(), s.getDrivingMinutes(), s.getIdleMinutes(), s.getNightDrivingMinutes(),
                s.getMaxSpeedKph(), s.getTripsCount(), s.getStartLatitude(), s.getStartLongitude(), s.getEndLatitude(), s.getEndLongitude(),
                s.getStartLocation(), s.getEndLocation(), s.isGpsIssue(), s.movementFlags(), s.getDataSource(), s.getComputedAt());
    }
}
