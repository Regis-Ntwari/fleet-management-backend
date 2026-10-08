package com.limoz.fleet.maintenance.dto;

import com.limoz.fleet.maintenance.WorkshopType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record WorkshopRequest(
        @NotBlank @Size(max = 120) String name,
        @NotNull WorkshopType workshopType,
        @Size(max = 120) String contactName,
        @Size(max = 30) String phone,
        @Email @Size(max = 150) String email,
        @Size(max = 255) String address,
        Boolean active) {}
