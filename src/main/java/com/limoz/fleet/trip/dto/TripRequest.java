package com.limoz.fleet.trip.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record TripRequest(
        @NotNull Long vehicleId,
        @NotNull Long driverId,
        Long customerId,
        @NotBlank @Size(max = 255) String origin,
        @NotBlank @Size(max = 255) String destination,
        @Size(max = 500) String routeDescription,
        @Size(max = 255) String purpose,
        @Size(max = 500) String passengerDetails,
        @Min(0) Integer passengers,
        @NotNull Instant scheduledStartAt,
        Instant scheduledEndAt,
        String notes) {}
