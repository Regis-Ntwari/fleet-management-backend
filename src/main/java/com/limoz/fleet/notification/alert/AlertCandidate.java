package com.limoz.fleet.notification.alert;

import com.limoz.fleet.notification.NotificationSeverity;

/**
 * A condition detected by an {@link AlertScanner} during one scan. The {@code dedupeKey} identifies the condition
 * across scans (e.g. {@code DOCUMENT_EXPIRED:VehicleDocument:42}): the same key refreshes the existing alert instead
 * of creating a new one, and keys that stop being produced cause their alert to be auto-resolved.
 *
 * @param type            alert type
 * @param severity        INFO / WARNING / CRITICAL
 * @param title           short headline (max 150 chars)
 * @param message         detail (max 1000 chars)
 * @param entityType      related record type, e.g. "Vehicle"
 * @param entityId        related record id
 * @param entityReference human reference (plate, driver name...)
 * @param linkPath        frontend route of the related record
 * @param dedupeKey       stable identity of the condition (max 200 chars)
 */
public record AlertCandidate(
        AlertType type,
        NotificationSeverity severity,
        String title,
        String message,
        String entityType,
        Long entityId,
        String entityReference,
        String linkPath,
        String dedupeKey) {

    /** Convenience key builder: {@code TYPE:EntityType:id[:qualifier]}. */
    public static String key(AlertType type, String entityType, Long entityId, String qualifier) {
        String base = type.name() + ":" + entityType + ":" + entityId;
        return qualifier == null || qualifier.isBlank() ? base : base + ":" + qualifier;
    }
}
