package com.limoz.fleet.audit.dto;

import com.fasterxml.jackson.annotation.JsonRawValue;
import com.limoz.fleet.audit.AuditAction;

import java.time.Instant;

public record AuditLogResponse(
        Long id,
        Long userId,
        String username,
        AuditAction action,
        String entityType,
        Long entityId,
        String entityReference,
        String description,
        @JsonRawValue String previousValue,
        @JsonRawValue String newValue,
        String ipAddress,
        Instant occurredAt) {}
