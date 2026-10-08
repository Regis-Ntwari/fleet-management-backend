package com.limoz.fleet.telematics.dto;

import com.limoz.fleet.telematics.domain.FuelSensorStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record FuelSensorStatusRequest(@NotNull FuelSensorStatus status, @Size(max = 255) String reason) {}
