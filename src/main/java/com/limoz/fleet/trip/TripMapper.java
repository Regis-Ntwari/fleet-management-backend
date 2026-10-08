package com.limoz.fleet.trip;

import com.limoz.fleet.customer.CustomerMapper;
import com.limoz.fleet.driver.DriverMapper;
import com.limoz.fleet.trip.dto.TripResponse;
import com.limoz.fleet.vehicle.VehicleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TripMapper {

    private final VehicleMapper vehicleMapper;
    private final DriverMapper driverMapper;
    private final CustomerMapper customerMapper;

    public TripResponse toResponse(Trip t) {
        return new TripResponse(t.getId(), t.getTripNumber(), vehicleMapper.toSummary(t.getVehicle()), driverMapper.toSummary(t.getDriver()),
                customerMapper.toSummary(t.getCustomer()),
                t.getBooking() == null ? null : t.getBooking().getId(),
                t.getBooking() == null ? null : t.getBooking().getBookingNumber(),
                t.getBookingSlotId(), t.getOrigin(), t.getDestination(), t.getRouteDescription(), t.getPurpose(),
                t.getPassengerDetails(), t.getPassengers(), t.getScheduledStartAt(), t.getScheduledEndAt(), t.getStartedAt(),
                t.getEndedAt(), t.getStartOdometerKm(), t.getEndOdometerKm(), t.getDistanceKm(), t.getDurationMinutes(),
                t.getFuelUsedLitres(), t.getMaxSpeedKph(), t.getStatus(), t.getCancellationReason(), t.getNotes(),
                t.getCreatedAt(), t.getUpdatedAt(), t.getCreatedBy());
    }
}
