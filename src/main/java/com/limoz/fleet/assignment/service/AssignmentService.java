package com.limoz.fleet.assignment.service;

import com.limoz.fleet.assignment.domain.AssignmentStatus;
import com.limoz.fleet.assignment.domain.VehicleAssignment;
import com.limoz.fleet.assignment.repository.VehicleAssignmentRepository;

import com.limoz.fleet.assignment.dto.AssignmentRequest;
import com.limoz.fleet.assignment.dto.AssignmentResponse;
import com.limoz.fleet.assignment.dto.EndAssignmentRequest;
import com.limoz.fleet.audit.domain.AuditAction;
import com.limoz.fleet.audit.service.AuditService;
import com.limoz.fleet.common.api.PageResponse;
import com.limoz.fleet.common.exception.BusinessRuleException;
import com.limoz.fleet.common.exception.ResourceNotFoundException;
import com.limoz.fleet.config.CacheConfig;
import com.limoz.fleet.driver.domain.Driver;
import com.limoz.fleet.driver.mapper.DriverMapper;
import com.limoz.fleet.driver.service.DriverService;
import com.limoz.fleet.driver.domain.DriverStatus;
import com.limoz.fleet.security.SecurityUtils;
import com.limoz.fleet.settings.domain.SettingKeys;
import com.limoz.fleet.settings.service.SettingsService;
import com.limoz.fleet.vehicle.service.OdometerService;
import com.limoz.fleet.vehicle.domain.OdometerSource;
import com.limoz.fleet.vehicle.domain.Vehicle;
import com.limoz.fleet.vehicle.mapper.VehicleMapper;
import com.limoz.fleet.vehicle.service.VehicleService;
import com.limoz.fleet.vehicle.domain.VehicleStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * Vehicle -> driver assignment with conflict prevention. Only one ACTIVE assignment per vehicle and per
 * driver can exist (also enforced by partial unique indexes). History is never overwritten.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class AssignmentService {

    private final VehicleAssignmentRepository repository;
    private final VehicleService vehicleService;
    private final DriverService driverService;
    private final OdometerService odometerService;
    private final VehicleMapper vehicleMapper;
    private final DriverMapper driverMapper;
    private final SettingsService settingsService;
    private final AuditService auditService;
    private final Clock clock;
    private final ZoneId operationalZone;

    @CacheEvict(cacheNames = CacheConfig.DASHBOARD, allEntries = true)
    public AssignmentResponse assign(AssignmentRequest request) {
        Vehicle vehicle = vehicleService.loadActive(request.vehicleId());
        Driver driver = driverService.loadActive(request.driverId());
        Instant now = Instant.now(clock);
        Instant startAt = request.startAt() == null ? now : request.startAt();

        if (!vehicle.getOperationalStatus().isDispatchable()) {
            throw new BusinessRuleException("VEHICLE_NOT_AVAILABLE",
                    "Vehicle " + vehicle.getPlateNumber() + " is " + vehicle.getOperationalStatus() + " and cannot be assigned");
        }
        repository.findByVehicleIdAndStatus(vehicle.getId(), AssignmentStatus.ACTIVE).ifPresent(a -> {
            throw new BusinessRuleException("VEHICLE_ALREADY_ASSIGNED",
                    "Vehicle " + vehicle.getPlateNumber() + " is already assigned to " + a.getDriver().getFullName());
        });
        if (!driver.getStatus().canBeAssigned()) {
            throw new BusinessRuleException("DRIVER_NOT_AVAILABLE",
                    "Driver " + driver.getFullName() + " is " + driver.getStatus() + " and cannot be assigned");
        }
        repository.findByDriverIdAndStatus(driver.getId(), AssignmentStatus.ACTIVE).ifPresent(a -> {
            throw new BusinessRuleException("DRIVER_ALREADY_ASSIGNED",
                    "Driver " + driver.getFullName() + " is already assigned to vehicle " + a.getVehicle().getPlateNumber());
        });
        if (settingsService.getBoolean(SettingKeys.DISPATCH_REQUIRE_VALID_LICENSE)
                && !driver.isLicenseValidOn(LocalDate.ofInstant(startAt, operationalZone))) {
            throw new BusinessRuleException("DRIVER_LICENSE_EXPIRED",
                    "Driver " + driver.getFullName() + "'s licence expired on " + driver.getLicenseExpiryDate());
        }

        VehicleAssignment assignment = new VehicleAssignment();
        assignment.setVehicle(vehicle);
        assignment.setDriver(driver);
        assignment.setStartAt(startAt);
        assignment.setPurpose(request.purpose());
        assignment.setComments(request.comments());
        assignment.setAssignedByUserId(SecurityUtils.currentUserId().orElse(null));
        long odometer = request.odometerAtAssignment() == null ? vehicle.getOdometerKm() : request.odometerAtAssignment();
        assignment.setOdometerAtAssignment(odometer);
        if (request.odometerAtAssignment() != null) {
            odometerService.record(vehicle, odometer, OdometerSource.ASSIGNMENT, "VehicleAssignment", null, startAt);
        }
        assignment = repository.save(assignment);

        vehicle.setCurrentDriver(driver);
        driver.setCurrentVehicle(vehicle);
        if (vehicle.getOperationalStatus() == VehicleStatus.AVAILABLE) {
            vehicleService.transition(vehicle, VehicleStatus.ASSIGNED, "Assigned to " + driver.getFullName());
        }
        if (driver.getStatus() == DriverStatus.AVAILABLE) {
            driverService.transition(driver, DriverStatus.ASSIGNED, "Assigned to " + vehicle.getPlateNumber());
        }
        AssignmentResponse response = toResponse(assignment);
        auditService.record(AuditAction.ASSIGN, "VehicleAssignment", assignment.getId(), vehicle.getPlateNumber(), null, response,
                "Vehicle " + vehicle.getPlateNumber() + " assigned to driver " + driver.getFullName());
        return response;
    }

    @CacheEvict(cacheNames = CacheConfig.DASHBOARD, allEntries = true)
    public AssignmentResponse end(Long id, EndAssignmentRequest request) {
        VehicleAssignment assignment = load(id);
        if (assignment.getStatus() != AssignmentStatus.ACTIVE) {
            throw new BusinessRuleException("Assignment is already " + assignment.getStatus());
        }
        Vehicle vehicle = assignment.getVehicle();
        Driver driver = assignment.getDriver();
        if (vehicle.getOperationalStatus() == VehicleStatus.ON_TRIP) {
            throw new BusinessRuleException("VEHICLE_ON_TRIP", "Complete the active trip before ending the assignment");
        }
        Instant endAt = request.endAt() == null ? Instant.now(clock) : request.endAt();
        if (endAt.isBefore(assignment.getStartAt())) {
            throw new BusinessRuleException("End date cannot precede the assignment start date");
        }
        AssignmentResponse before = toResponse(assignment);
        assignment.setEndAt(endAt);
        assignment.setStatus(request.cancelled() ? AssignmentStatus.CANCELLED : AssignmentStatus.COMPLETED);
        assignment.setEndedByUserId(SecurityUtils.currentUserId().orElse(null));
        if (request.comments() != null) {
            assignment.setComments(request.comments());
        }
        if (request.odometerAtReturn() != null) {
            if (assignment.getOdometerAtAssignment() != null && request.odometerAtReturn() < assignment.getOdometerAtAssignment()) {
                throw new BusinessRuleException("Odometer at return cannot be lower than odometer at assignment");
            }
            assignment.setOdometerAtReturn(request.odometerAtReturn());
            odometerService.record(vehicle, request.odometerAtReturn(), OdometerSource.ASSIGNMENT, "VehicleAssignment", id, endAt);
        } else {
            assignment.setOdometerAtReturn(vehicle.getOdometerKm());
        }
        vehicle.setCurrentDriver(null);
        driver.setCurrentVehicle(null);
        if (vehicle.getOperationalStatus() == VehicleStatus.ASSIGNED) {
            vehicleService.transition(vehicle, VehicleStatus.AVAILABLE, "Assignment ended");
        }
        if (driver.getStatus() == DriverStatus.ASSIGNED) {
            driverService.transition(driver, DriverStatus.AVAILABLE, "Assignment ended");
        }
        AssignmentResponse after = toResponse(repository.save(assignment));
        auditService.record(AuditAction.UNASSIGN, "VehicleAssignment", id, vehicle.getPlateNumber(), before, after,
                "Assignment of " + vehicle.getPlateNumber() + " to " + driver.getFullName() + " ended");
        return after;
    }

    @Transactional(readOnly = true)
    public AssignmentResponse get(Long id) {
        return toResponse(load(id));
    }

    @Transactional(readOnly = true)
    public List<AssignmentResponse> active() {
        return repository.findByStatusOrderByStartAtDesc(AssignmentStatus.ACTIVE).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<AssignmentResponse> historyForVehicle(Long vehicleId, Pageable pageable) {
        return PageResponse.from(repository.findByVehicleIdOrderByStartAtDesc(vehicleId, pageable).map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public PageResponse<AssignmentResponse> historyForDriver(Long driverId, Pageable pageable) {
        return PageResponse.from(repository.findByDriverIdOrderByStartAtDesc(driverId, pageable).map(this::toResponse));
    }

    private VehicleAssignment load(Long id) {
        return repository.findDetailedById(id).orElseThrow(() -> new ResourceNotFoundException("Assignment", id));
    }

    public AssignmentResponse toResponse(VehicleAssignment a) {
        Long distance = a.getOdometerAtAssignment() != null && a.getOdometerAtReturn() != null
                ? a.getOdometerAtReturn() - a.getOdometerAtAssignment() : null;
        return new AssignmentResponse(a.getId(), vehicleMapper.toSummary(a.getVehicle()), driverMapper.toSummary(a.getDriver()),
                a.getStartAt(), a.getEndAt(), a.getAssignedByUserId(), a.getEndedByUserId(), a.getPurpose(),
                a.getOdometerAtAssignment(), a.getOdometerAtReturn(), distance, a.getStatus(), a.getComments(),
                a.getCreatedAt(), a.getCreatedBy());
    }
}
