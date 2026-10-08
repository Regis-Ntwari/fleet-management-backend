package com.limoz.fleet.trip.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TripCancelRequest(@NotBlank(message = "A cancellation reason is required") @Size(max = 255) String reason) {}
