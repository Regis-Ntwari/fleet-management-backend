package com.limoz.fleet.trip.repository;

import com.limoz.fleet.trip.domain.Trip;

import com.limoz.fleet.common.util.DateRanges;
import com.limoz.fleet.common.util.Specifications;
import com.limoz.fleet.trip.dto.TripFilter;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

public final class TripSpecifications {

    private TripSpecifications() {}

    public static Specification<Trip> from(TripFilter f, ZoneId zone) {
        return Specifications.and(
                Specifications.likeAny(f.q(), "tripNumber", "origin", "destination"),
                Specifications.equal("vehicle.id", f.vehicleId()),
                Specifications.equal("driver.id", f.driverId()),
                Specifications.equal("customer.id", f.customerId()),
                Specifications.equal("booking.id", f.bookingId()),
                Specifications.in("status", f.status()),
                scheduledBetween(f.from(), f.to(), zone));
    }

    /** Scheduled start within the operational-day range [from 00:00, to 24:00). */
    private static Specification<Trip> scheduledBetween(LocalDate from, LocalDate to, ZoneId zone) {
        if (from == null && to == null) return null;
        Instant lower = from == null ? null : DateRanges.forDate(from, zone).from();
        Instant upper = to == null ? null : DateRanges.forDate(to, zone).to();
        return (root, query, cb) -> {
            if (lower != null && upper != null) {
                return cb.and(cb.greaterThanOrEqualTo(root.get("scheduledStartAt"), lower), cb.lessThan(root.get("scheduledStartAt"), upper));
            }
            if (lower != null) return cb.greaterThanOrEqualTo(root.get("scheduledStartAt"), lower);
            return cb.lessThan(root.get("scheduledStartAt"), upper);
        };
    }
}
