package com.limoz.fleet.document.dto;

import com.limoz.fleet.document.domain.DocumentAppliesTo;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record DocumentTypeRequest(
        @NotBlank @Size(max = 40) @Pattern(regexp = "^[A-Z][A-Z0-9_]*$") String code,
        @NotBlank @Size(max = 100) String name,
        @NotNull DocumentAppliesTo appliesTo,
        boolean requiredForDispatch,
        @Min(0) Integer warningDays,
        Boolean active,
        Integer sortOrder) {}
