package com.limoz.fleet.document.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DocumentRequest(
        @NotNull Long documentTypeId,
        @Size(max = 80) String documentNumber,
        @Size(max = 120) String issuer,
        LocalDate issueDate,
        LocalDate expiryDate,
        Long attachmentId,
        @DecimalMin("0") BigDecimal cost,
        @Size(max = 500) String notes) {}
