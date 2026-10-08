package com.limoz.fleet.maintenance.dto;

import com.limoz.fleet.maintenance.WorkshopType;

public record WorkshopResponse(Long id, String name, WorkshopType workshopType, String contactName, String phone, String email,
                               String address, boolean active) {}
