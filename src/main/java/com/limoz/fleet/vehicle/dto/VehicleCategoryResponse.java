package com.limoz.fleet.vehicle.dto;

import java.math.BigDecimal;

public record VehicleCategoryResponse(Long id, String code, String name, String description, Integer minSeats, Integer maxSeats,
                                      BigDecimal defaultDayRate, boolean active, int sortOrder) {}
