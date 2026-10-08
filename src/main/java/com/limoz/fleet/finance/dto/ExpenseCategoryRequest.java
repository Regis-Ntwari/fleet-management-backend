package com.limoz.fleet.finance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ExpenseCategoryRequest(
        @NotBlank @Size(max = 40) @Pattern(regexp = "^[A-Z][A-Z0-9_]*$", message = "code must be UPPER_SNAKE_CASE") String code,
        @NotBlank @Size(max = 80) String name,
        Boolean active,
        Integer sortOrder) {}
