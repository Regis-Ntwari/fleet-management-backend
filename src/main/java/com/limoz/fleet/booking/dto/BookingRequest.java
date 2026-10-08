package com.limoz.fleet.booking.dto;

import com.limoz.fleet.booking.domain.BookingSource;
import com.limoz.fleet.booking.domain.ServiceType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalTime;
import java.util.List;

/**
 * Create / update payload of the New Booking wizard. Booking dates are derived from the lines
 * (earliest start, latest end); {@code confirm = true} creates the booking directly as CONFIRMED.
 */
public record BookingRequest(
        @NotNull(message = "Client is required") Long customerId,
        Long commitmentId,
        @Size(max = 120) String contactName,
        @Size(max = 30) String contactPhone,
        @Email @Size(max = 150) String contactEmail,
        ServiceType serviceType,
        @Size(max = 255) String pickupLocation,
        @Size(max = 255) String dropoffLocation,
        LocalTime pickupTime,
        LocalTime returnTime,
        @Min(0) Integer passengers,
        @Pattern(regexp = "^[A-Z]{3}$", message = "currency must be a 3-letter ISO code") String currency,
        BookingSource source,
        String notes,
        @NotEmpty(message = "At least one booking line is required") @Valid List<BookingLineRequest> lines,
        Boolean confirm) {}
