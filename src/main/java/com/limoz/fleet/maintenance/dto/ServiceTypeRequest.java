package com.limoz.fleet.maintenance.dto;

import com.limoz.fleet.maintenance.domain.ServiceCategory;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ServiceTypeRequest(
        @NotBlank @Size(max = 40) @Pattern(regexp = "[A-Za-z0-9_\\-]+", message = "code may only contain letters, digits, '_' and '-'") String code,
        @NotBlank @Size(max = 100) String name,
        ServiceCategory category,
        @Min(1) Integer defaultIntervalKm,
        @Min(1) Integer defaultIntervalDays,
        Boolean active,
        Integer sortOrder) {}
