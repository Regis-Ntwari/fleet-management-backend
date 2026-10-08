package com.limoz.fleet.notification.service;

import com.limoz.fleet.notification.domain.NotificationSeverity;

import com.limoz.fleet.common.event.OperationalEvent;
import com.limoz.fleet.security.Roles;
import com.limoz.fleet.vehicle.domain.VehicleStatus;
import com.limoz.fleet.vehicle.domain.VehicleStatusChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Set;

/**
 * Turns domain events into notifications once the publishing transaction has committed, so a notification never
 * refers to a change that was rolled back. Runs in its own transaction ({@code REQUIRES_NEW}) because the
 * publishing transaction is already completed in the AFTER_COMMIT phase.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OperationalEventListener {

    public static final String VEHICLE_UNAVAILABLE = "VEHICLE_UNAVAILABLE";

    private final NotificationService notificationService;
    private final Clock clock;

    /** Dedupe key {@code type:entityType:entityId:yyyy-MM-dd}: one notification per event, record and day. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onOperationalEvent(OperationalEvent event) {
        String dedupeKey = event.type() + ":" + event.entityType() + ":" + event.entityId() + ":" + LocalDate.now(clock);
        int created = notificationService.notifyRoles(event.targetRoles(), event.type(), NotificationSeverity.from(event.severity()),
                event.title(), event.message(), event.entityType(), event.entityId(), event.linkPath(), dedupeKey);
        log.debug("Event {} ({} {}) -> {} notification(s)", event.type(), event.entityType(), event.entityId(), created);
    }

    /** Dispatchers and fleet managers hear when a vehicle drops out of the dispatchable pool. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onVehicleStatusChanged(VehicleStatusChangedEvent event) {
        if (event.to() != VehicleStatus.OUT_OF_SERVICE && event.to() != VehicleStatus.IN_MAINTENANCE) {
            return;
        }
        String status = event.to() == VehicleStatus.OUT_OF_SERVICE ? "out of service" : "in maintenance";
        String message = "Vehicle " + event.plateNumber() + " is now " + status + " (was " + event.from() + ")"
                + (event.reason() == null || event.reason().isBlank() ? "" : ": " + event.reason());
        String dedupeKey = VEHICLE_UNAVAILABLE + ":Vehicle:" + event.vehicleId() + ":" + event.to() + ":" + LocalDate.now(clock);
        notificationService.notifyRoles(Set.of(Roles.DISPATCHER, Roles.FLEET_MANAGER), VEHICLE_UNAVAILABLE, NotificationSeverity.INFO,
                "Vehicle unavailable: " + event.plateNumber(), message, "Vehicle", event.vehicleId(),
                "/vehicles/" + event.vehicleId(), dedupeKey);
    }
}
