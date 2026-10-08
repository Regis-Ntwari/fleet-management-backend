package com.limoz.fleet.document.dto;

import com.limoz.fleet.document.domain.DocumentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record DocumentResponse(
        Long id,
        String ownerType,
        Long ownerId,
        String ownerReference,
        Long documentTypeId,
        String documentTypeCode,
        String documentTypeName,
        boolean requiredForDispatch,
        String documentNumber,
        String issuer,
        LocalDate issueDate,
        LocalDate expiryDate,
        Long daysToExpiry,
        DocumentStatus status,
        Long attachmentId,
        BigDecimal cost,
        String notes,
        boolean superseded,
        Instant createdAt,
        Instant updatedAt) {}
