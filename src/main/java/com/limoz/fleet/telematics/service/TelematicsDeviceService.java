package com.limoz.fleet.telematics.service;

import com.limoz.fleet.telematics.domain.FuelSensorStatus;
import com.limoz.fleet.telematics.domain.GpsStatus;
import com.limoz.fleet.telematics.domain.TelematicsDevice;
import com.limoz.fleet.telematics.mapper.TelematicsMapper;
import com.limoz.fleet.telematics.repository.TelematicsDeviceRepository;

import com.limoz.fleet.audit.domain.AuditAction;
import com.limoz.fleet.audit.service.AuditService;
import com.limoz.fleet.common.event.OperationalEvent;
import com.limoz.fleet.common.event.Severity;
import com.limoz.fleet.common.exception.BusinessRuleException;
import com.limoz.fleet.common.exception.DuplicateResourceException;
import com.limoz.fleet.common.exception.ResourceNotFoundException;
import com.limoz.fleet.config.CacheConfig;
import com.limoz.fleet.security.Roles;
import com.limoz.fleet.settings.domain.SettingKeys;
import com.limoz.fleet.settings.service.SettingsService;
import com.limoz.fleet.telematics.dto.DeviceFilter;
import com.limoz.fleet.telematics.dto.DeviceProblemsResponse;
import com.limoz.fleet.telematics.dto.DeviceRequest;
import com.limoz.fleet.telematics.dto.DeviceResponse;
import com.limoz.fleet.telematics.dto.FuelSensorStatusRequest;
import com.limoz.fleet.telematics.dto.GpsRefreshResponse;
import com.limoz.fleet.vehicle.domain.Vehicle;
import com.limoz.fleet.vehicle.service.VehicleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Registration and health of GPS trackers. One device per vehicle; the vehicle of a device never changes
 * (deactivate the old device and register a new one when a tracker is moved).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class TelematicsDeviceService {

    public static final String DEFAULT_PROVIDER = "manual";

    private final TelematicsDeviceRepository deviceRepository;
    private final VehicleService vehicleService;
    private final SettingsService settingsService;
    private final TelematicsMapper mapper;
    private final AuditService auditService;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<DeviceResponse> list(DeviceFilter filter) {
        return deviceRepository.findForActiveVehicles(filter.gpsStatus(), filter.fuelSensorStatus(), filter.active())
                .stream().map(mapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public DeviceResponse get(Long id) {
        return mapper.toResponse(load(id));
    }

    @Transactional(readOnly = true)
    public DeviceResponse forVehicle(Long vehicleId) {
        vehicleService.load(vehicleId);
        return deviceRepository.findByVehicleId(vehicleId).map(mapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("No telematics device is registered for vehicle " + vehicleId));
    }

    @Transactional(readOnly = true)
    public DeviceProblemsResponse problems() {
        List<GpsStatus> gpsProblems = Arrays.stream(GpsStatus.values()).filter(GpsStatus::isProblem).toList();
        List<FuelSensorStatus> fuelProblems = Arrays.stream(FuelSensorStatus.values()).filter(FuelSensorStatus::isProblem).toList();
        List<TelematicsDevice> devices = deviceRepository.findProblems(gpsProblems, fuelProblems);
        List<DeviceResponse> gps = devices.stream().filter(d -> d.getGpsStatus().isProblem()).map(mapper::toResponse).toList();
        List<DeviceResponse> fuel = devices.stream().filter(d -> d.getFuelSensorStatus().isProblem()).map(mapper::toResponse).toList();
        return new DeviceProblemsResponse(gps.size(), fuel.size(), gps, fuel);
    }

    @Transactional(readOnly = true)
    public TelematicsDevice load(Long id) {
        return deviceRepository.findDetailedById(id).orElseThrow(() -> new ResourceNotFoundException("Telematics device", id));
    }

    @CacheEvict(cacheNames = CacheConfig.DASHBOARD, allEntries = true)
    public DeviceResponse register(DeviceRequest request) {
        Vehicle vehicle = vehicleService.loadActive(request.vehicleId());
        if (deviceRepository.existsByVehicleId(vehicle.getId())) {
            throw new DuplicateResourceException("Vehicle " + vehicle.getPlateNumber() + " already has a telematics device");
        }
        String provider = providerCode(request);
        String externalId = externalId(request);
        if (externalId != null && deviceRepository.externalIdExists(provider, externalId, null)) {
            throw new DuplicateResourceException("Device " + externalId + " of provider " + provider + " is already registered");
        }
        TelematicsDevice device = new TelematicsDevice();
        device.setVehicle(vehicle);
        device.setProviderCode(provider);
        device.setExternalDeviceId(externalId);
        apply(device, request);
        device.setFuelSensorStatus(request.fuelSensorStatus() == null ? FuelSensorStatus.NOT_INSTALLED : request.fuelSensorStatus());
        device.setGpsStatus(GpsStatus.NO_SIGNAL);
        DeviceResponse response = mapper.toResponse(deviceRepository.save(device));
        auditService.record(AuditAction.CREATE, "TelematicsDevice", device.getId(), vehicle.getPlateNumber(), null, response,
                "Telematics device registered on " + vehicle.getPlateNumber());
        return response;
    }

    public DeviceResponse update(Long id, DeviceRequest request) {
        TelematicsDevice device = load(id);
        if (!device.getVehicle().getId().equals(request.vehicleId())) {
            throw new BusinessRuleException("DEVICE_VEHICLE_IMMUTABLE",
                    "A device cannot be moved to another vehicle; deactivate it and register a new device instead");
        }
        String provider = providerCode(request);
        String externalId = externalId(request);
        if (externalId != null && deviceRepository.externalIdExists(provider, externalId, id)) {
            throw new DuplicateResourceException("Device " + externalId + " of provider " + provider + " is already registered");
        }
        DeviceResponse before = mapper.toResponse(device);
        device.setProviderCode(provider);
        device.setExternalDeviceId(externalId);
        apply(device, request);
        if (request.fuelSensorStatus() != null) {
            device.setFuelSensorStatus(request.fuelSensorStatus());
        }
        DeviceResponse after = mapper.toResponse(deviceRepository.save(device));
        auditService.record(AuditAction.UPDATE, "TelematicsDevice", id, device.getVehicle().getPlateNumber(), before, after,
                "Telematics device updated");
        return after;
    }

    @CacheEvict(cacheNames = CacheConfig.DASHBOARD, allEntries = true)
    public DeviceResponse deactivate(Long id, String reason) {
        TelematicsDevice device = load(id);
        if (!device.isActive()) {
            throw new BusinessRuleException("DEVICE_ALREADY_INACTIVE", "Device is already inactive");
        }
        DeviceResponse before = mapper.toResponse(device);
        device.setActive(false);
        device.setGpsStatus(GpsStatus.DISCONNECTED);
        DeviceResponse after = mapper.toResponse(deviceRepository.save(device));
        auditService.record(AuditAction.DISABLE, "TelematicsDevice", id, device.getVehicle().getPlateNumber(), before, after,
                "Telematics device deactivated" + (reason == null || reason.isBlank() ? "" : ": " + reason));
        return after;
    }

    @CacheEvict(cacheNames = CacheConfig.DASHBOARD, allEntries = true)
    public DeviceResponse activate(Long id) {
        TelematicsDevice device = load(id);
        if (device.isActive()) {
            throw new BusinessRuleException("DEVICE_ALREADY_ACTIVE", "Device is already active");
        }
        DeviceResponse before = mapper.toResponse(device);
        device.setActive(true);
        device.setGpsStatus(expectedGpsStatus(device, Instant.now(clock), offlineThreshold()));
        DeviceResponse after = mapper.toResponse(deviceRepository.save(device));
        auditService.record(AuditAction.ENABLE, "TelematicsDevice", id, device.getVehicle().getPlateNumber(), before, after,
                "Telematics device re-activated");
        return after;
    }

    @CacheEvict(cacheNames = CacheConfig.DASHBOARD, allEntries = true)
    public DeviceResponse setFuelSensorStatus(Long id, FuelSensorStatusRequest request) {
        TelematicsDevice device = load(id);
        FuelSensorStatus from = device.getFuelSensorStatus();
        if (from == request.status()) {
            return mapper.toResponse(device);
        }
        device.setFuelSensorStatus(request.status());
        DeviceResponse after = mapper.toResponse(deviceRepository.save(device));
        auditService.record(AuditAction.STATUS_CHANGE, "TelematicsDevice", id, device.getVehicle().getPlateNumber(),
                Map.of("fuelSensorStatus", from), Map.of("fuelSensorStatus", request.status()),
                "Fuel sensor " + from + " -> " + request.status() + (request.reason() == null ? "" : ": " + request.reason()));
        return after;
    }

    /**
     * Recomputes the GPS status of every device from its last communication time and the
     * {@code telematics.gps_offline_minutes} setting. A device that turns OFFLINE raises a GPS_OFFLINE event.
     */
    @CacheEvict(cacheNames = CacheConfig.DASHBOARD, allEntries = true)
    public GpsRefreshResponse refreshGpsStatuses() {
        Instant now = Instant.now(clock);
        Duration threshold = offlineThreshold();
        int changed = 0;
        int online = 0;
        int offline = 0;
        int noSignal = 0;
        int disconnected = 0;
        List<TelematicsDevice> devices = deviceRepository.findAllWithVehicle();
        for (TelematicsDevice device : devices) {
            if (applyGpsStatus(device, now, threshold)) {
                changed++;
            }
            switch (device.getGpsStatus()) {
                case ONLINE -> online++;
                case OFFLINE -> offline++;
                case NO_SIGNAL -> noSignal++;
                case DISCONNECTED -> disconnected++;
                default -> { }
            }
        }
        if (changed > 0) {
            log.info("GPS status refresh: {} device(s) changed status", changed);
        }
        return new GpsRefreshResponse(devices.size(), changed, online, offline, noSignal, disconnected);
    }

    /**
     * Applies the expected GPS status to a managed device. Returns true when the status changed; publishes
     * {@code GPS_OFFLINE} when the device transitions into OFFLINE.
     */
    public boolean applyGpsStatus(TelematicsDevice device, Instant now, Duration offlineThreshold) {
        GpsStatus from = device.getGpsStatus();
        GpsStatus to = expectedGpsStatus(device, now, offlineThreshold);
        if (from == to) {
            return false;
        }
        device.setGpsStatus(to);
        if (to == GpsStatus.OFFLINE) {
            Vehicle vehicle = device.getVehicle();
            long minutes = Duration.between(device.getLastCommunicationAt(), now).toMinutes();
            events.publishEvent(OperationalEvent.of("GPS_OFFLINE", Severity.WARNING,
                    "GPS offline: " + vehicle.getPlateNumber(),
                    "No GPS communication from " + vehicle.getPlateNumber() + " for " + minutes + " minutes (last seen "
                            + device.getLastCommunicationAt() + ")",
                    "Vehicle", vehicle.getId(), vehicle.getPlateNumber(), "/vehicles/" + vehicle.getId(),
                    Roles.FLEET_MANAGER, Roles.IT_ADMIN));
        }
        return true;
    }

    public Duration offlineThreshold() {
        return Duration.ofMinutes(settingsService.getInt(SettingKeys.GPS_OFFLINE_MINUTES));
    }

    public static GpsStatus expectedGpsStatus(TelematicsDevice device, Instant now, Duration offlineThreshold) {
        if (!device.isActive()) {
            return GpsStatus.DISCONNECTED;
        }
        if (device.getLastCommunicationAt() == null) {
            return GpsStatus.NO_SIGNAL;
        }
        return device.getLastCommunicationAt().isBefore(now.minus(offlineThreshold)) ? GpsStatus.OFFLINE : GpsStatus.ONLINE;
    }

    private static String providerCode(DeviceRequest r) {
        return r.providerCode() == null || r.providerCode().isBlank() ? DEFAULT_PROVIDER : r.providerCode().trim().toLowerCase();
    }

    private static String externalId(DeviceRequest r) {
        return r.externalDeviceId() == null || r.externalDeviceId().isBlank() ? null : r.externalDeviceId().trim();
    }

    private static void apply(TelematicsDevice d, DeviceRequest r) {
        d.setSimNumber(r.simNumber() == null || r.simNumber().isBlank() ? null : r.simNumber().trim());
        d.setInstalledAt(r.installedAt());
        d.setNotes(r.notes());
    }
}
