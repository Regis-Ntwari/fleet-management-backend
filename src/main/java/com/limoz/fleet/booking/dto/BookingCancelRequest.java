package com.limoz.fleet.booking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BookingCancelRequest(@NotBlank(message = "A cancellation reason is required") @Size(max = 255) String reason) {}
