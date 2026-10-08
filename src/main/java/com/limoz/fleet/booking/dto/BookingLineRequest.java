package com.limoz.fleet.booking.dto;

import com.limoz.fleet.booking.domain.PricingType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record BookingLineRequest(
        @NotNull(message = "Vehicle category is required") Long categoryId,
        @Size(max = 100) String preferredModel,
        @NotNull @Min(1) @Max(500) Integer quantity,
        @NotNull PricingType pricingType,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        @NotNull @DecimalMin("0") BigDecimal unitPrice,
        @Size(max = 255) String notes) {}
