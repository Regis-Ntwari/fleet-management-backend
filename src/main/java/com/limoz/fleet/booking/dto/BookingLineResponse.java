package com.limoz.fleet.booking.dto;

import com.limoz.fleet.booking.PricingType;

import java.math.BigDecimal;
import java.time.LocalDate;

public record BookingLineResponse(Long id, Long categoryId, String categoryName, String preferredModel, int quantity,
                                  PricingType pricingType, LocalDate startDate, LocalDate endDate, long billableUnits,
                                  BigDecimal unitPrice, BigDecimal lineTotal, String notes) {}
