package com.limoz.fleet.driver.dto;

import com.limoz.fleet.driver.DriverStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DriverStatusChangeRequest(@NotNull DriverStatus status, @Size(max = 255) String reason) {}
