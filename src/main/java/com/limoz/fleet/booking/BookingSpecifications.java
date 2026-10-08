package com.limoz.fleet.booking;

import com.limoz.fleet.booking.dto.BookingFilter;
import com.limoz.fleet.booking.dto.VoucherFilter;
import com.limoz.fleet.common.util.Specifications;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public final class BookingSpecifications {

    private BookingSpecifications() {}

    public static Specification<Booking> from(BookingFilter f) {
        List<BookingStatus> statuses = f.status() == null ? new ArrayList<>() : new ArrayList<>(f.status());
        if (f.readyFor() != null) {
            statuses = statuses.isEmpty() ? f.readyFor().statuses()
                    : statuses.stream().filter(f.readyFor().statuses()::contains).toList();
            if (statuses.isEmpty()) {
                return (root, query, cb) -> cb.disjunction();
            }
        }
        return Specifications.and(
                Specifications.likeAny(f.q(), "bookingNumber", "customer.name"),
                Specifications.in("status", statuses),
                Specifications.equal("customer.id", f.customerId()),
                Specifications.equal("commitmentId", f.commitmentId()),
                overlapping(f.from(), f.to()));
    }

    public static Specification<DeploymentVoucher> from(VoucherFilter f) {
        return Specifications.and(
                Specifications.likeAny(f.q(), "voucherNumber", "booking.bookingNumber", "customer.name", "vehicle.plateNumber"),
                Specifications.in("status", f.status()),
                Specifications.equal("customer.id", f.customerId()),
                Specifications.equal("vehicle.id", f.vehicleId()),
                Specifications.equal("driver.id", f.driverId()),
                Specifications.equal("booking.id", f.bookingId()),
                Specifications.equal("slot.id", f.slotId()),
                Specifications.dateBetween("voucherDate", f.from(), f.to()));
    }

    /** Bookings whose [startDate, endDate] period overlaps the requested range. */
    private static Specification<Booking> overlapping(LocalDate from, LocalDate to) {
        if (from == null && to == null) return null;
        return (root, query, cb) -> {
            if (from != null && to != null) {
                return cb.and(cb.lessThanOrEqualTo(root.get("startDate"), to), cb.greaterThanOrEqualTo(root.get("endDate"), from));
            }
            if (from != null) return cb.greaterThanOrEqualTo(root.get("endDate"), from);
            return cb.lessThanOrEqualTo(root.get("startDate"), to);
        };
    }
}
