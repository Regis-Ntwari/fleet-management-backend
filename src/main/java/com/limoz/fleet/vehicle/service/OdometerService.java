package com.limoz.fleet.vehicle.service;

import com.limoz.fleet.vehicle.domain.OdometerLog;
import com.limoz.fleet.vehicle.domain.OdometerSource;
import com.limoz.fleet.vehicle.domain.Vehicle;
import com.limoz.fleet.vehicle.repository.OdometerLogRepository;

import com.limoz.fleet.audit.domain.AuditAction;
import com.limoz.fleet.audit.service.AuditService;
import com.limoz.fleet.common.exception.BusinessRuleException;
import com.limoz.fleet.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;

/**
 * Single entry point for odometer changes. Readings can only increase; a decrease requires an
 * administrator-approved correction (VEHICLE_ODOMETER_CORRECT) and is journaled with its reason.
 */
@Service
@RequiredArgsConstructor
public class OdometerService {

    private final OdometerLogRepository logRepository;
    private final AuditService auditService;
    private final Clock clock;

    /**
     * Records a reading from an operational event. A reading lower than the current odometer is rejected;
     * a reading equal to the current value is accepted silently (no new log entry).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(Vehicle vehicle, long readingKm, OdometerSource source, String referenceType, Long referenceId) {
        record(vehicle, readingKm, source, referenceType, referenceId, Instant.now(clock));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(Vehicle vehicle, long readingKm, OdometerSource source, String referenceType, Long referenceId, Instant at) {
        if (readingKm < 0) {
            throw new BusinessRuleException("INVALID_ODOMETER", "Odometer reading cannot be negative");
        }
        long current = vehicle.getOdometerKm();
        if (readingKm < current) {
            throw new BusinessRuleException("ODOMETER_DECREASE",
                    "Odometer reading " + readingKm + " km is lower than the vehicle's current reading of " + current
                            + " km. Use an administrator-approved odometer correction if the current reading is wrong.");
        }
        if (readingKm == current && !logRepository.findByVehicleIdOrderByRecordedAtDesc(vehicle.getId(), org.springframework.data.domain.PageRequest.of(0, 1)).isEmpty()) {
            return;
        }
        vehicle.setOdometerKm(readingKm);
        log(vehicle.getId(), readingKm, current, source, referenceType, referenceId, null, at);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void correct(Vehicle vehicle, long readingKm, String reason) {
        long previous = vehicle.getOdometerKm();
        vehicle.setOdometerKm(readingKm);
        log(vehicle.getId(), readingKm, previous, OdometerSource.CORRECTION, "Vehicle", vehicle.getId(), reason, Instant.now(clock));
        auditService.record(AuditAction.CORRECTION, "Vehicle", vehicle.getId(), vehicle.getPlateNumber(),
                Map.of("odometerKm", previous), Map.of("odometerKm", readingKm), "Odometer corrected: " + reason);
    }

    private void log(Long vehicleId, long reading, long previous, OdometerSource source, String refType, Long refId, String reason, Instant at) {
        OdometerLog entry = new OdometerLog();
        entry.setVehicleId(vehicleId);
        entry.setReadingKm(reading);
        entry.setPreviousKm(previous);
        entry.setSource(source);
        entry.setReferenceType(refType);
        entry.setReferenceId(refId);
        entry.setCorrectionReason(reason);
        entry.setRecordedAt(at);
        entry.setRecordedBy(SecurityUtils.currentUsername().orElse("system"));
        logRepository.save(entry);
    }
}
