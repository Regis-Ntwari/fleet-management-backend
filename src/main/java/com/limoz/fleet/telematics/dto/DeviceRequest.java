package com.limoz.fleet.telematics.dto;

import com.limoz.fleet.telematics.FuelSensorStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** Registration / update payload of a telematics device. The vehicle cannot change after registration. */
public record DeviceRequest(
        @NotNull Long vehicleId,
        @Size(max = 30) String providerCode,
        @Size(max = 80) String externalDeviceId,
        @Size(max = 30) String simNumber,
        LocalDate installedAt,
        FuelSensorStatus fuelSensorStatus,
        @Size(max = 255) String notes) {}
