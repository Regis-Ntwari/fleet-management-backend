package com.limoz.fleet.maintenance.dto;

import com.limoz.fleet.maintenance.domain.ServiceCategory;

public record ServiceTypeResponse(Long id, String code, String name, ServiceCategory category, Integer defaultIntervalKm,
                                  Integer defaultIntervalDays, boolean active, int sortOrder) {}
