package com.limoz.fleet.maintenance;

import com.limoz.fleet.audit.AuditAction;
import com.limoz.fleet.audit.AuditService;
import com.limoz.fleet.common.api.PageResponse;
import com.limoz.fleet.common.event.OperationalEvent;
import com.limoz.fleet.common.event.Severity;
import com.limoz.fleet.common.exception.BusinessRuleException;
import com.limoz.fleet.common.exception.DuplicateResourceException;
import com.limoz.fleet.common.exception.ResourceNotFoundException;
import com.limoz.fleet.common.util.Specifications;
import com.limoz.fleet.config.CacheConfig;
import com.limoz.fleet.maintenance.dto.ScheduleFilter;
import com.limoz.fleet.maintenance.dto.ScheduleRefreshResponse;
import com.limoz.fleet.maintenance.dto.ScheduleRequest;
import com.limoz.fleet.maintenance.dto.ScheduleResponse;
import com.limoz.fleet.security.Roles;
import com.limoz.fleet.settings.SettingKeys;
import com.limoz.fleet.settings.SettingsService;
import com.limoz.fleet.vehicle.MaintenanceStatus;
import com.limoz.fleet.vehicle.Vehicle;
import com.limoz.fleet.vehicle.VehicleRepository;
import com.limoz.fleet.vehicle.VehicleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Preventive maintenance schedules (vehicle x service type). Keeps {@code next_service_*} and the
 * OK / DUE_SOON / OVERDUE status current and rolls the worst schedule up into the vehicle's maintenance status.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class MaintenanceScheduleService {

    private final MaintenanceScheduleRepository repository;
    private final MaintenanceRecordRepository recordRepository;
    private final VehicleRepository vehicleRepository;
    private final VehicleService vehicleService;
    private final ServiceTypeService serviceTypeService;
    private final SettingsService settingsService;
    private final MaintenanceMapper mapper;
    private final AuditService auditService;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    // ---------------------------------------------------------------- queries

    @Transactional(readOnly = true)
    public PageResponse<ScheduleResponse> search(ScheduleFilter f, Pageable pageable) {
        Specification<MaintenanceSchedule> spec = Specifications.and(
                f.active() == null ? Specifications.equal("active", true) : Specifications.equal("active", f.active()),
                Specifications.isFalse("vehicle.archived"),
                Specifications.in("status", f.status()),
                Specifications.equal("vehicle.id", f.vehicleId()),
                Specifications.equal("serviceType.id", f.serviceTypeId()));
        return PageResponse.from(repository.findAll(spec, pageable).map(mapper::toResponse));
    }

    @Transactional(readOnly = true)
    public List<ScheduleResponse> forVehicle(Long vehicleId) {
        vehicleService.load(vehicleId);
        return repository.findByVehicleIdOrderByNextServiceDateAscNextServiceOdometerAsc(vehicleId).stream().map(mapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public ScheduleResponse get(Long id) {
        return mapper.toResponse(load(id));
    }

    // ---------------------------------------------------------------- commands

    @CacheEvict(cacheNames = CacheConfig.DASHBOARD, allEntries = true)
    public ScheduleResponse create(ScheduleRequest request) {
        Vehicle vehicle = vehicleService.loadActive(request.vehicleId());
        ServiceType type = serviceTypeService.loadActive(request.serviceTypeId());
        if (repository.findByVehicleIdAndServiceTypeId(vehicle.getId(), type.getId()).isPresent()) {
            throw new DuplicateResourceException("Vehicle " + vehicle.getPlateNumber() + " already has a " + type.getName() + " schedule");
        }
        MaintenanceSchedule schedule = new MaintenanceSchedule();
        schedule.setVehicle(vehicle);
        schedule.setServiceType(type);
        apply(schedule, request);
        recompute(schedule);
        ScheduleResponse response = mapper.toResponse(repository.save(schedule));
        refreshVehicle(vehicle);
        auditService.record(AuditAction.CREATE, "MaintenanceSchedule", schedule.getId(), vehicle.getPlateNumber(), null, response,
                type.getName() + " schedule created for " + vehicle.getPlateNumber());
        return response;
    }

    @CacheEvict(cacheNames = CacheConfig.DASHBOARD, allEntries = true)
    public ScheduleResponse update(Long id, ScheduleRequest request) {
        MaintenanceSchedule schedule = load(id);
        if (!schedule.getVehicle().getId().equals(request.vehicleId()) || !schedule.getServiceType().getId().equals(request.serviceTypeId())) {
            throw new BusinessRuleException("SCHEDULE_KEY_IMMUTABLE", "Vehicle and service type of a schedule cannot be changed; create a new schedule");
        }
        ScheduleResponse before = mapper.toResponse(schedule);
        apply(schedule, request);
        recompute(schedule);
        ScheduleResponse after = mapper.toResponse(repository.save(schedule));
        refreshVehicle(schedule.getVehicle());
        auditService.record(AuditAction.UPDATE, "MaintenanceSchedule", id, schedule.getVehicle().getPlateNumber(), before, after, "Maintenance schedule updated");
        return after;
    }

    @CacheEvict(cacheNames = CacheConfig.DASHBOARD, allEntries = true)
    public void deactivate(Long id) {
        MaintenanceSchedule schedule = load(id);
        if (!schedule.isActive()) {
            return;
        }
        ScheduleResponse before = mapper.toResponse(schedule);
        schedule.setActive(false);
        repository.save(schedule);
        refreshVehicle(schedule.getVehicle());
        auditService.record(AuditAction.DISABLE, "MaintenanceSchedule", id, schedule.getVehicle().getPlateNumber(), before,
                mapper.toResponse(schedule), "Maintenance schedule deactivated");
    }

    /**
     * Called when a job completes a task linked to a service type: resets the schedule's last service point
     * (creating the schedule from the service type's defaults when the vehicle has none yet).
     */
    public MaintenanceSchedule recordServiceDone(Vehicle vehicle, ServiceType type, MaintenanceRecord record, long odometerKm, LocalDate date) {
        MaintenanceSchedule schedule = repository.findByVehicleIdAndServiceTypeId(vehicle.getId(), type.getId()).orElseGet(() -> {
            MaintenanceSchedule created = new MaintenanceSchedule();
            created.setVehicle(vehicle);
            created.setServiceType(type);
            created.setIntervalKm(defaultIntervalKm(type));
            created.setIntervalDays(defaultIntervalDays(type));
            return created;
        });
        ScheduleResponse before = schedule.getId() == null ? null : mapper.toResponse(schedule);
        schedule.setActive(true);
        schedule.setLastServiceOdometer(odometerKm);
        schedule.setLastServiceDate(date);
        schedule.setLastMaintenanceRecordId(record.getId());
        recompute(schedule);
        schedule = repository.save(schedule);
        auditService.record(before == null ? AuditAction.CREATE : AuditAction.UPDATE, "MaintenanceSchedule", schedule.getId(),
                vehicle.getPlateNumber(), before, mapper.toResponse(schedule),
                type.getName() + " done on " + record.getMaintenanceNumber() + " at " + odometerKm + " km");
        return schedule;
    }

    /** Recomputes the vehicle's active schedules and its maintenance status (IN_WORKSHOP / SERVICE_OVERDUE / SERVICE_DUE_SOON / OK). */
    public MaintenanceStatus refreshVehicle(Vehicle vehicle) {
        List<MaintenanceSchedule> schedules = repository.findByVehicleIdAndActiveTrue(vehicle.getId());
        for (MaintenanceSchedule schedule : schedules) {
            ScheduleStatus previous = schedule.getStatus();
            recompute(schedule);
            if (schedule.getStatus() != previous) {
                repository.save(schedule);
                notifyTransition(schedule);
            }
        }
        boolean inWorkshop = recordRepository.existsByVehicleIdAndStatusIn(vehicle.getId(), MaintenanceRecordStatus.IN_WORKSHOP);
        MaintenanceStatus status = vehicleStatus(schedules, inWorkshop);
        vehicle.setMaintenanceStatus(status);
        return status;
    }

    /** Nightly job / manual refresh: every active schedule and every vehicle's maintenance status. */
    @CacheEvict(cacheNames = CacheConfig.DASHBOARD, allEntries = true)
    public ScheduleRefreshResponse refreshAll() {
        List<MaintenanceSchedule> schedules = repository.findByActiveTrueAndVehicleArchivedFalse();
        int changed = 0;
        int dueSoon = 0;
        int overdue = 0;
        for (MaintenanceSchedule schedule : schedules) {
            ScheduleStatus previous = schedule.getStatus();
            recompute(schedule);
            if (schedule.getStatus() != previous) {
                changed++;
                repository.save(schedule);
                notifyTransition(schedule);
            }
            if (schedule.getStatus() == ScheduleStatus.DUE_SOON) dueSoon++;
            if (schedule.getStatus() == ScheduleStatus.OVERDUE) overdue++;
        }
        Map<Long, List<MaintenanceSchedule>> byVehicle = schedules.stream().collect(Collectors.groupingBy(s -> s.getVehicle().getId()));
        Set<Long> inWorkshop = recordRepository.vehicleIdsWithStatusIn(MaintenanceRecordStatus.IN_WORKSHOP);
        int vehiclesUpdated = 0;
        for (Vehicle vehicle : vehicleRepository.findByArchivedFalseOrderByPlateNumberAsc()) {
            MaintenanceStatus status = vehicleStatus(byVehicle.getOrDefault(vehicle.getId(), List.of()), inWorkshop.contains(vehicle.getId()));
            if (vehicle.getMaintenanceStatus() != status) {
                vehicle.setMaintenanceStatus(status);
                vehiclesUpdated++;
            }
        }
        if (changed > 0 || vehiclesUpdated > 0) {
            auditService.recordSystem(AuditAction.SYSTEM, "MaintenanceSchedule", null, null,
                    "Schedule refresh: " + changed + " schedule(s) and " + vehiclesUpdated + " vehicle status(es) changed");
            log.info("Maintenance schedule refresh: {} schedule(s) changed, {} vehicle(s) updated", changed, vehiclesUpdated);
        }
        return new ScheduleRefreshResponse(schedules.size(), changed, vehiclesUpdated, dueSoon, overdue);
    }

    // ---------------------------------------------------------------- helpers

    private MaintenanceSchedule load(Long id) {
        return repository.findDetailedById(id).orElseThrow(() -> new ResourceNotFoundException("Maintenance schedule", id));
    }

    private void apply(MaintenanceSchedule s, ScheduleRequest r) {
        ServiceType type = s.getServiceType();
        s.setIntervalKm(r.intervalKm() != null ? r.intervalKm() : defaultIntervalKm(type));
        s.setIntervalDays(r.intervalDays() != null ? r.intervalDays() : defaultIntervalDays(type));
        if (s.getIntervalKm() == null && s.getIntervalDays() == null) {
            throw new BusinessRuleException("SCHEDULE_INTERVAL_REQUIRED", "A schedule needs a kilometre or day interval");
        }
        s.setLastServiceOdometer(r.lastServiceOdometer() != null ? r.lastServiceOdometer()
                : s.getLastServiceOdometer() != null ? s.getLastServiceOdometer() : s.getVehicle().getOdometerKm());
        s.setLastServiceDate(r.lastServiceDate() != null ? r.lastServiceDate()
                : s.getLastServiceDate() != null ? s.getLastServiceDate() : LocalDate.now(clock));
        s.setActive(r.active() == null || r.active());
        s.setNotes(r.notes() == null || r.notes().isBlank() ? null : r.notes().trim());
    }

    private Integer defaultIntervalKm(ServiceType type) {
        if (type.getDefaultIntervalKm() != null) return type.getDefaultIntervalKm();
        return type.getDefaultIntervalDays() != null ? null : settingsService.getInt(SettingKeys.MAINTENANCE_DEFAULT_INTERVAL_KM);
    }

    private Integer defaultIntervalDays(ServiceType type) {
        if (type.getDefaultIntervalDays() != null) return type.getDefaultIntervalDays();
        return type.getDefaultIntervalKm() != null ? null : settingsService.getInt(SettingKeys.MAINTENANCE_DEFAULT_INTERVAL_DAYS);
    }

    private void recompute(MaintenanceSchedule s) {
        ScheduleCalculator.Next next = ScheduleCalculator.next(s.getLastServiceOdometer(), s.getLastServiceDate(), s.getIntervalKm(), s.getIntervalDays());
        s.setNextServiceOdometer(next.nextServiceOdometer());
        s.setNextServiceDate(next.nextServiceDate());
        s.setStatus(ScheduleCalculator.status(s.getVehicle().getOdometerKm(), LocalDate.now(clock), next.nextServiceOdometer(),
                next.nextServiceDate(), settingsService.getInt(SettingKeys.MAINTENANCE_DUE_SOON_KM),
                settingsService.getInt(SettingKeys.MAINTENANCE_DUE_SOON_DAYS)));
    }

    private static MaintenanceStatus vehicleStatus(Collection<MaintenanceSchedule> schedules, boolean inWorkshop) {
        if (inWorkshop) return MaintenanceStatus.IN_WORKSHOP;
        ScheduleStatus worst = schedules.stream().filter(MaintenanceSchedule::isActive).map(MaintenanceSchedule::getStatus)
                .max(Enum::compareTo).orElse(ScheduleStatus.OK);
        return switch (worst) {
            case OVERDUE -> MaintenanceStatus.SERVICE_OVERDUE;
            case DUE_SOON -> MaintenanceStatus.SERVICE_DUE_SOON;
            case OK -> MaintenanceStatus.OK;
        };
    }

    private void notifyTransition(MaintenanceSchedule s) {
        if (s.getStatus() == ScheduleStatus.OK) return;
        Vehicle vehicle = s.getVehicle();
        boolean overdue = s.getStatus() == ScheduleStatus.OVERDUE;
        String when = (s.getNextServiceOdometer() == null ? "" : " at " + s.getNextServiceOdometer() + " km")
                + (s.getNextServiceDate() == null ? "" : " by " + s.getNextServiceDate());
        events.publishEvent(OperationalEvent.of(overdue ? "MAINTENANCE_OVERDUE" : "MAINTENANCE_DUE", Severity.WARNING,
                (overdue ? "Service overdue: " : "Service due soon: ") + vehicle.getPlateNumber(),
                s.getServiceType().getName() + " for " + vehicle.getDisplayName() + " is " + (overdue ? "overdue" : "due") + when
                        + " (vehicle at " + vehicle.getOdometerKm() + " km)",
                "MaintenanceSchedule", s.getId(), vehicle.getPlateNumber(), "/vehicles/" + vehicle.getId() + "/maintenance",
                Roles.WORKSHOP_MANAGER, Roles.FLEET_MANAGER));
    }
}
