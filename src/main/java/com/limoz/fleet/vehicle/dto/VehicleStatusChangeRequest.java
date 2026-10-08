package com.limoz.fleet.vehicle.dto;

import com.limoz.fleet.vehicle.VehicleStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record VehicleStatusChangeRequest(@NotNull VehicleStatus status, @Size(max = 255) String reason) {}
