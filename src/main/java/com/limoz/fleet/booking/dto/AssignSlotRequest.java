package com.limoz.fleet.booking.dto;

import com.limoz.fleet.booking.domain.Shift;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Assign (or replace) the vehicle and driver of a slot. {@code allowCategoryMismatch} lets a dispatcher
 * deliberately deploy a vehicle of another category than the one the client booked.
 */
public record AssignSlotRequest(
        @NotNull Long vehicleId,
        @NotNull Long driverId,
        Shift shift,
        boolean allowCategoryMismatch,
        @Size(max = 255) String notes) {}
