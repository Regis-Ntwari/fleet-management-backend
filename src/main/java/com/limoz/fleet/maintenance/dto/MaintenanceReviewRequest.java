package com.limoz.fleet.maintenance.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;
import java.util.List;

/** Mechanic review: diagnosis and repair plan; moves a REPORTED job to INSPECTION and requests the listed parts. */
public record MaintenanceReviewRequest(
        @NotBlank String diagnosis,
        List<String> observedFaults,
        @NotBlank String recommendedRepair,
        String labourNotes,
        LocalDate reviewDate,
        LocalDate expectedCompletionAt,
        @Valid List<MaintenancePartRequest> parts) {}
