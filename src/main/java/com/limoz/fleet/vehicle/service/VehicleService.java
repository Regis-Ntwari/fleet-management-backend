package com.limoz.fleet.vehicle.service;

import com.limoz.fleet.vehicle.domain.OdometerSource;
import com.limoz.fleet.vehicle.domain.OwnershipType;
import com.limoz.fleet.vehicle.domain.Vehicle;
import com.limoz.fleet.vehicle.domain.VehicleStatus;
import com.limoz.fleet.vehicle.domain.VehicleStatusChangedEvent;
import com.limoz.fleet.vehicle.mapper.VehicleMapper;
import com.limoz.fleet.vehicle.repository.OdometerLogRepository;
import com.limoz.fleet.vehicle.repository.VehicleRepository;
import com.limoz.fleet.vehicle.repository.VehicleSpecifications;

import com.limoz.fleet.audit.domain.AuditAction;
import com.limoz.fleet.audit.service.AuditService;
import com.limoz.fleet.common.api.PageResponse;
import com.limoz.fleet.common.exception.BusinessRuleException;
import com.limoz.fleet.common.exception.DuplicateResourceException;
import com.limoz.fleet.common.exception.ResourceNotFoundException;
import com.limoz.fleet.config.CacheConfig;
import com.limoz.fleet.vehicle.dto.OdometerCorrectionRequest;
import com.limoz.fleet.vehicle.dto.OdometerLogResponse;
import com.limoz.fleet.vehicle.dto.VehicleFilter;
import com.limoz.fleet.vehicle.dto.VehicleRequest;
import com.limoz.fleet.vehicle.dto.VehicleResponse;
import com.limoz.fleet.vehicle.dto.VehicleStatusChangeRequest;
import com.limoz.fleet.vehicle.dto.VehicleSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional
public class VehicleService {

    private final VehicleRepository vehicleRepository;
    private final VehicleCategoryService categoryService;
    private final OdometerService odometerService;
    private final OdometerLogRepository odometerLogRepository;
    private final VehicleMapper mapper;
    private final AuditService auditService;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    @Transactional(readOnly = true)
    public PageResponse<VehicleResponse> search(VehicleFilter filter, Pageable pageable) {
        return PageResponse.from(vehicleRepository.findAll(VehicleSpecifications.from(filter), pageable).map(mapper::toResponse));
    }

    @Transactional(readOnly = true)
    public VehicleResponse get(Long id) {
        return mapper.toResponse(load(id));
    }

    @Transactional(readOnly = true)
    public List<VehicleSummary> summaries(List<VehicleStatus> statuses) {
        List<Vehicle> vehicles = statuses == null || statuses.isEmpty()
                ? vehicleRepository.findByArchivedFalseOrderByPlateNumberAsc()
                : vehicleRepository.findByArchivedFalseAndOperationalStatusInOrderByPlateNumberAsc(statuses);
        return vehicles.stream().map(mapper::toSummary).toList();
    }

    @Transactional(readOnly = true)
    public Vehicle load(Long id) {
        return vehicleRepository.findDetailedById(id).orElseThrow(() -> new ResourceNotFoundException("Vehicle", id));
    }

    /** Loads an active (non-archived) vehicle for operational use by other modules. */
    @Transactional(readOnly = true)
    public Vehicle loadActive(Long id) {
        Vehicle vehicle = load(id);
        if (vehicle.isArchived()) {
            throw new BusinessRuleException("VEHICLE_ARCHIVED", "Vehicle " + vehicle.getPlateNumber() + " is archived");
        }
        return vehicle;
    }

    @CacheEvict(cacheNames = CacheConfig.DASHBOARD, allEntries = true)
    public VehicleResponse create(VehicleRequest request) {
        String plate = Vehicle.normalisePlate(request.plateNumber());
        if (vehicleRepository.plateExists(plate, null)) {
            throw new DuplicateResourceException("Vehicle plate number " + plate + " already exists");
        }
        Vehicle vehicle = new Vehicle();
        apply(vehicle, request);
        vehicle.setPlateNumber(plate);
        vehicle.setOdometerKm(request.odometerKm() == null ? 0 : request.odometerKm());
        vehicle = vehicleRepository.saveAndFlush(vehicle);
        if (vehicle.getOdometerKm() > 0) {
            odometerService.record(vehicle, vehicle.getOdometerKm(), OdometerSource.MANUAL, "Vehicle", vehicle.getId());
        }
        VehicleResponse response = mapper.toResponse(vehicle);
        auditService.record(AuditAction.CREATE, "Vehicle", vehicle.getId(), plate, null, response, "Vehicle registered");
        return response;
    }

    @CacheEvict(cacheNames = CacheConfig.DASHBOARD, allEntries = true)
    public VehicleResponse update(Long id, VehicleRequest request) {
        Vehicle vehicle = load(id);
        VehicleResponse before = mapper.toResponse(vehicle);
        String plate = Vehicle.normalisePlate(request.plateNumber());
        if (vehicleRepository.plateExists(plate, id)) {
            throw new DuplicateResourceException("Vehicle plate number " + plate + " already exists");
        }
        apply(vehicle, request);
        vehicle.setPlateNumber(plate);
        if (request.odometerKm() != null && request.odometerKm() != vehicle.getOdometerKm()) {
            odometerService.record(vehicle, request.odometerKm(), OdometerSource.MANUAL, "Vehicle", vehicle.getId());
        }
        VehicleResponse after = mapper.toResponse(vehicleRepository.save(vehicle));
        auditService.record(AuditAction.UPDATE, "Vehicle", id, plate, before, after, "Vehicle updated");
        return after;
    }

    @CacheEvict(cacheNames = CacheConfig.DASHBOARD, allEntries = true)
    public VehicleResponse changeStatus(Long id, VehicleStatusChangeRequest request) {
        Vehicle vehicle = load(id);
        VehicleStatus from = vehicle.getOperationalStatus();
        VehicleStatus to = request.status();
        if (from == to) {
            return mapper.toResponse(vehicle);
        }
        if ((from == VehicleStatus.ON_TRIP || from == VehicleStatus.IN_MAINTENANCE) && to != VehicleStatus.OUT_OF_SERVICE) {
            throw new BusinessRuleException("STATUS_MANAGED_BY_WORKFLOW",
                    "Vehicle is " + from + "; complete the active trip or maintenance job instead of changing the status manually");
        }
        if (to == VehicleStatus.ON_TRIP || to == VehicleStatus.IN_MAINTENANCE) {
            throw new BusinessRuleException("STATUS_MANAGED_BY_WORKFLOW",
                    "Status " + to + " is set automatically by trips and maintenance jobs");
        }
        if (to == VehicleStatus.AVAILABLE && vehicle.getCurrentDriver() != null) {
            to = VehicleStatus.ASSIGNED;
        }
        transition(vehicle, to, request.reason());
        return mapper.toResponse(vehicleRepository.save(vehicle));
    }

    /**
     * Workflow-driven status change used by trips, maintenance, assignments and dispatch.
     * Publishes {@link VehicleStatusChangedEvent} and writes an audit entry.
     */
    @CacheEvict(cacheNames = CacheConfig.DASHBOARD, allEntries = true)
    public void transition(Vehicle vehicle, VehicleStatus to, String reason) {
        VehicleStatus from = vehicle.getOperationalStatus();
        if (from == to) return;
        vehicle.setOperationalStatus(to);
        auditService.record(AuditAction.STATUS_CHANGE, "Vehicle", vehicle.getId(), vehicle.getPlateNumber(),
                Map.of("operationalStatus", from), Map.of("operationalStatus", to),
                "Vehicle status " + from + " -> " + to + (reason == null ? "" : ": " + reason));
        events.publishEvent(new VehicleStatusChangedEvent(vehicle.getId(), vehicle.getPlateNumber(), from, to, reason));
    }

    /** Status to return to when a trip/maintenance ends: ASSIGNED if a driver is attached, else AVAILABLE. */
    public VehicleStatus restingStatus(Vehicle vehicle) {
        return vehicle.getCurrentDriver() != null ? VehicleStatus.ASSIGNED : VehicleStatus.AVAILABLE;
    }

    public void correctOdometer(Long id, OdometerCorrectionRequest request) {
        Vehicle vehicle = load(id);
        odometerService.correct(vehicle, request.readingKm(), request.reason());
        vehicleRepository.save(vehicle);
    }

    @Transactional(readOnly = true)
    public PageResponse<OdometerLogResponse> odometerHistory(Long id, Pageable pageable) {
        load(id);
        return PageResponse.from(odometerLogRepository.findByVehicleIdOrderByRecordedAtDesc(id, pageable).map(mapper::toResponse));
    }

    @CacheEvict(cacheNames = CacheConfig.DASHBOARD, allEntries = true)
    public void archive(Long id) {
        Vehicle vehicle = load(id);
        if (vehicle.getOperationalStatus() == VehicleStatus.ON_TRIP || vehicle.getOperationalStatus() == VehicleStatus.IN_MAINTENANCE) {
            throw new BusinessRuleException("Vehicle cannot be archived while on a trip or in maintenance");
        }
        if (vehicle.getCurrentDriver() != null) {
            throw new BusinessRuleException("Release the assigned driver before archiving the vehicle");
        }
        VehicleResponse before = mapper.toResponse(vehicle);
        vehicle.setArchived(true);
        vehicle.setArchivedAt(Instant.now(clock));
        vehicle.setOperationalStatus(VehicleStatus.INACTIVE);
        vehicleRepository.save(vehicle);
        auditService.record(AuditAction.ARCHIVE, "Vehicle", id, vehicle.getPlateNumber(), before, mapper.toResponse(vehicle), "Vehicle archived");
    }

    @CacheEvict(cacheNames = CacheConfig.DASHBOARD, allEntries = true)
    public VehicleResponse restore(Long id) {
        Vehicle vehicle = load(id);
        if (!vehicle.isArchived()) {
            throw new BusinessRuleException("Vehicle is not archived");
        }
        vehicle.setArchived(false);
        vehicle.setArchivedAt(null);
        vehicle.setOperationalStatus(VehicleStatus.AVAILABLE);
        VehicleResponse after = mapper.toResponse(vehicleRepository.save(vehicle));
        auditService.record(AuditAction.RESTORE, "Vehicle", id, vehicle.getPlateNumber(), null, after, "Vehicle restored");
        return after;
    }

    private void apply(Vehicle v, VehicleRequest r) {
        v.setFleetNumber(r.fleetNumber() == null || r.fleetNumber().isBlank() ? null : r.fleetNumber().trim().toUpperCase());
        v.setMake(r.make().trim());
        v.setModel(r.model().trim());
        v.setModelYear(r.modelYear());
        v.setCategory(categoryService.load(r.categoryId()));
        v.setBodyType(r.bodyType());
        v.setFuelType(r.fuelType());
        v.setTransmission(r.transmission());
        v.setEngineNumber(r.engineNumber());
        v.setChassisNumber(r.chassisNumber() == null || r.chassisNumber().isBlank() ? null : r.chassisNumber().trim().toUpperCase());
        v.setColor(r.color());
        v.setSeatingCapacity(r.seatingCapacity());
        v.setPurchaseDate(r.purchaseDate());
        v.setAcquisitionCost(r.acquisitionCost());
        v.setOwnershipType(r.ownershipType() == null ? OwnershipType.OWNED : r.ownershipType());
        v.setOwnerName(r.ownerName());
        v.setOwnerContact(r.ownerContact());
        v.setOwnerDriverName(r.ownerDriverName());
        v.setInsuranceProvider(r.insuranceProvider());
        v.setInsurancePolicyNumber(r.insurancePolicyNumber());
        v.setInsuranceExpiryDate(r.insuranceExpiryDate());
        v.setDayRate(r.dayRate());
        v.setDepartment(r.department());
        v.setNotes(r.notes());
    }
}
