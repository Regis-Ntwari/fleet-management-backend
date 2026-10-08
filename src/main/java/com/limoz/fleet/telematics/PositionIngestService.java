package com.limoz.fleet.telematics;

import com.limoz.fleet.audit.AuditAction;
import com.limoz.fleet.audit.AuditService;
import com.limoz.fleet.config.CacheConfig;
import com.limoz.fleet.telematics.dto.IngestResult;
import com.limoz.fleet.telematics.dto.PositionInput;
import com.limoz.fleet.vehicle.OdometerService;
import com.limoz.fleet.vehicle.OdometerSource;
import com.limoz.fleet.vehicle.Vehicle;
import com.limoz.fleet.vehicle.VehicleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Validates and stores position batches coming from the API, CSV imports or the provider sync.
 * Rules: unknown/archived vehicles, invalid coordinates and future timestamps are rejected row by row;
 * a sample already stored for the same vehicle and timestamp is counted as a duplicate; the newest sample
 * updates the device's last-known fields; an odometer reading above the vehicle's odometer is journaled
 * with source TELEMATICS (readings below it are ignored, the odometer never decreases).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class PositionIngestService {

    public static final int MAX_BATCH = 5000;
    /** Samples may be slightly ahead of the server clock (device clock skew); beyond this they are rejected. */
    static final Duration FUTURE_TOLERANCE = Duration.ofMinutes(5);

    private static final BigDecimal MAX_LAT = new BigDecimal("90");
    private static final BigDecimal MAX_LON = new BigDecimal("180");
    private static final BigDecimal MAX_SPEED = new BigDecimal("400");

    private final VehiclePositionRepository positionRepository;
    private final TelematicsDeviceRepository deviceRepository;
    private final VehicleRepository vehicleRepository;
    private final TelematicsDeviceService deviceService;
    private final PositionCsvParser csvParser;
    private final OdometerService odometerService;
    private final AuditService auditService;
    private final Clock clock;

    /**
     * Ingests a batch. {@code source} is stored on each position ("api", "csv" or the provider code).
     */
    @CacheEvict(cacheNames = CacheConfig.DASHBOARD, allEntries = true)
    public IngestResult ingest(List<PositionInput> rows, String source) {
        return ingest(rows, source, Map.of());
    }

    /**
     * Imports a CSV file (see {@link PositionCsvParser}). Rows that fail to parse are reported with the parser's
     * reason; the remaining rows go through the same validation as a JSON batch.
     */
    @CacheEvict(cacheNames = CacheConfig.DASHBOARD, allEntries = true)
    public IngestResult importCsv(InputStream csv, String source) {
        PositionCsvParser.ParsedCsv parsed = csvParser.parse(csv, MAX_BATCH);
        Map<Integer, String> parseErrors = new HashMap<>();
        parsed.errors().forEach(e -> parseErrors.put(e.row(), e.reason()));
        return ingest(parsed.rows(), source, parseErrors);
    }

    /** @param parseErrors reasons for rows handed in as {@code null} placeholders (1-based row number) */
    private IngestResult ingest(List<PositionInput> rows, String source, Map<Integer, String> parseErrors) {
        Instant now = Instant.now(clock);
        Instant latestAccepted = now.plus(FUTURE_TOLERANCE);
        Duration offlineThreshold = deviceService.offlineThreshold();
        List<IngestResult.RowError> errors = new ArrayList<>();
        Map<String, Optional<Vehicle>> vehicleCache = new HashMap<>();
        Map<Long, TelematicsDevice> deviceCache = new HashMap<>();
        Map<Long, Vehicle> touchedVehicles = new HashMap<>();
        Map<Long, VehiclePosition> newestPerVehicle = new HashMap<>();
        Set<String> seenInBatch = new HashSet<>();
        int imported = 0;
        int duplicates = 0;

        for (int i = 0; i < rows.size(); i++) {
            int rowNumber = i + 1;
            PositionInput row = rows.get(i);
            if (row == null) {
                errors.add(new IngestResult.RowError(rowNumber, parseErrors.getOrDefault(rowNumber, "Empty row")));
                continue;
            }
            String problem = validate(row, latestAccepted);
            if (problem != null) {
                errors.add(new IngestResult.RowError(rowNumber, problem));
                continue;
            }
            Optional<Vehicle> resolved = resolveVehicle(row, vehicleCache);
            if (resolved.isEmpty()) {
                errors.add(new IngestResult.RowError(rowNumber, "Unknown vehicle (" + identifier(row) + ")"));
                continue;
            }
            Vehicle vehicle = resolved.get();
            if (vehicle.isArchived()) {
                errors.add(new IngestResult.RowError(rowNumber, "Vehicle " + vehicle.getPlateNumber() + " is archived"));
                continue;
            }
            String key = vehicle.getId() + "@" + row.recordedAt().toEpochMilli();
            if (!seenInBatch.add(key) || positionRepository.existsByVehicleIdAndRecordedAt(vehicle.getId(), row.recordedAt())) {
                duplicates++;
                continue;
            }
            TelematicsDevice device = deviceCache.computeIfAbsent(vehicle.getId(),
                    id -> deviceRepository.findByVehicleId(id).orElse(null));
            VehiclePosition position = toPosition(row, vehicle, device, source);
            positionRepository.save(position);
            imported++;
            touchedVehicles.put(vehicle.getId(), vehicle);
            VehiclePosition newest = newestPerVehicle.get(vehicle.getId());
            if (newest == null || position.getRecordedAt().isAfter(newest.getRecordedAt())) {
                newestPerVehicle.put(vehicle.getId(), position);
            }
        }

        for (Map.Entry<Long, VehiclePosition> entry : newestPerVehicle.entrySet()) {
            Vehicle vehicle = touchedVehicles.get(entry.getKey());
            VehiclePosition newest = entry.getValue();
            TelematicsDevice device = deviceCache.get(entry.getKey());
            if (device != null) {
                updateDevice(device, newest, now, offlineThreshold);
            }
            recordOdometer(vehicle, newest);
        }

        IngestResult result = new IngestResult(rows.size(), imported, duplicates, errors.size(), List.copyOf(errors));
        if (imported > 0 || !errors.isEmpty()) {
            auditService.record(AuditAction.IMPORT, "VehiclePosition", null, source, null,
                    Map.of("received", result.received(), "imported", imported, "duplicates", duplicates, "rejected", errors.size()),
                    "Position batch (" + source + "): " + imported + " imported, " + duplicates + " duplicate(s), " + errors.size() + " rejected");
        }
        log.debug("Position ingest from {}: {}", source, result);
        return result;
    }

    private static String validate(PositionInput row, Instant latestAccepted) {
        if (row.recordedAt() == null) return "recordedAt is required";
        if (row.recordedAt().isAfter(latestAccepted)) return "recordedAt is in the future";
        if (row.latitude() == null || row.latitude().abs().compareTo(MAX_LAT) > 0) return "latitude must be between -90 and 90";
        if (row.longitude() == null || row.longitude().abs().compareTo(MAX_LON) > 0) return "longitude must be between -180 and 180";
        if (row.speedKph() != null && (row.speedKph().signum() < 0 || row.speedKph().compareTo(MAX_SPEED) > 0)) return "speedKph must be between 0 and 400";
        if (row.odometerKm() != null && row.odometerKm().signum() < 0) return "odometerKm cannot be negative";
        if (row.heading() != null && (row.heading().signum() < 0 || row.heading().compareTo(new BigDecimal("360")) > 0)) return "heading must be between 0 and 360";
        if ((row.vehicleId() == null) && isBlank(row.plateNumber()) && isBlank(row.externalDeviceId())) return "vehicleId, plateNumber or externalDeviceId is required";
        return null;
    }

    private Optional<Vehicle> resolveVehicle(PositionInput row, Map<String, Optional<Vehicle>> cache) {
        String key = identifier(row);
        return cache.computeIfAbsent(key, k -> {
            if (row.vehicleId() != null) {
                return vehicleRepository.findById(row.vehicleId());
            }
            if (!isBlank(row.plateNumber())) {
                return vehicleRepository.findByPlate(row.plateNumber().trim());
            }
            List<TelematicsDevice> devices = deviceRepository.findByExternalDeviceId(row.externalDeviceId().trim());
            return devices.size() == 1 ? Optional.of(devices.getFirst().getVehicle()) : Optional.empty();
        });
    }

    private static String identifier(PositionInput row) {
        if (row.vehicleId() != null) return "id " + row.vehicleId();
        if (!isBlank(row.plateNumber())) return "plate " + row.plateNumber().trim();
        return "device " + (row.externalDeviceId() == null ? "" : row.externalDeviceId().trim());
    }

    private static VehiclePosition toPosition(PositionInput row, Vehicle vehicle, TelematicsDevice device, String source) {
        VehiclePosition p = new VehiclePosition();
        p.setVehicleId(vehicle.getId());
        p.setDeviceId(device == null ? null : device.getId());
        p.setRecordedAt(row.recordedAt());
        p.setLatitude(scale(row.latitude(), 6));
        p.setLongitude(scale(row.longitude(), 6));
        p.setSpeedKph(row.speedKph() == null ? BigDecimal.ZERO.setScale(1) : scale(row.speedKph(), 1));
        p.setHeading(scale(row.heading(), 1));
        p.setOdometerKm(scale(row.odometerKm(), 1));
        p.setIgnitionOn(row.ignitionOn());
        p.setBatteryVoltage(scale(row.batteryVoltage(), 2));
        p.setFuelLevelLitres(scale(row.fuelLevelLitres(), 2));
        p.setSource(source == null || source.isBlank() ? "provider" : source.length() > 20 ? source.substring(0, 20) : source);
        return p;
    }

    private void updateDevice(TelematicsDevice device, VehiclePosition newest, Instant now, Duration offlineThreshold) {
        if (device.getLastCommunicationAt() != null && !newest.getRecordedAt().isAfter(device.getLastCommunicationAt())) {
            return;
        }
        device.setLastCommunicationAt(newest.getRecordedAt());
        device.setLastLatitude(newest.getLatitude());
        device.setLastLongitude(newest.getLongitude());
        device.setLastSpeedKph(newest.getSpeedKph());
        if (newest.getOdometerKm() != null) {
            device.setLastOdometerKm(newest.getOdometerKm().setScale(0, RoundingMode.DOWN).longValue());
        }
        device.setLastIgnitionOn(newest.getIgnitionOn());
        if (newest.getBatteryVoltage() != null) {
            device.setLastBatteryVoltage(newest.getBatteryVoltage());
        }
        deviceService.applyGpsStatus(device, now, offlineThreshold);
        deviceRepository.save(device);
    }

    private void recordOdometer(Vehicle vehicle, VehiclePosition newest) {
        if (newest.getOdometerKm() == null) {
            return;
        }
        long reading = newest.getOdometerKm().setScale(0, RoundingMode.DOWN).longValue();
        if (reading > vehicle.getOdometerKm()) {
            odometerService.record(vehicle, reading, OdometerSource.TELEMATICS, "VehiclePosition", newest.getId(), newest.getRecordedAt());
            vehicleRepository.save(vehicle);
        }
    }

    private static BigDecimal scale(BigDecimal value, int scale) {
        return value == null ? null : value.setScale(scale, RoundingMode.HALF_UP);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
