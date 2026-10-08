package com.limoz.fleet.common.event;

import java.util.Set;

/**
 * Generic business event published by any module (via {@code ApplicationEventPublisher}) when something
 * operationally relevant happens - incident created, maintenance completed, booking assigned, fuel anomaly...
 * The notification module turns it into in-app notifications for users holding one of {@code targetRoles}
 * (and, later, email/SMS/WhatsApp through additional channels) without the publishing module knowing about
 * notifications at all.
 *
 * @param type            UPPER_SNAKE_CASE event type (e.g. INCIDENT_CREATED, MAINTENANCE_COMPLETED)
 * @param severity        INFO / WARNING / CRITICAL
 * @param title           short headline
 * @param message         human-readable detail
 * @param entityType      e.g. "Vehicle", "Trip", "MaintenanceRecord"
 * @param entityId        id of the related record
 * @param entityReference human reference (plate, trip number...)
 * @param linkPath        frontend route to open (e.g. /vehicles/12)
 * @param targetRoles     role codes that should be notified; empty = fleet managers and management
 */
public record OperationalEvent(
        String type,
        Severity severity,
        String title,
        String message,
        String entityType,
        Long entityId,
        String entityReference,
        String linkPath,
        Set<String> targetRoles) {

    public static OperationalEvent of(String type, Severity severity, String title, String message,
                                      String entityType, Long entityId, String entityReference, String linkPath, String... roles) {
        return new OperationalEvent(type, severity, title, message, entityType, entityId, entityReference, linkPath, Set.of(roles));
    }
}
