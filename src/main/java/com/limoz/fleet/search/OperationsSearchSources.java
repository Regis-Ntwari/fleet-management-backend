package com.limoz.fleet.search;

import com.limoz.fleet.booking.Booking;
import com.limoz.fleet.booking.BookingRepository;
import com.limoz.fleet.booking.DeploymentVoucher;
import com.limoz.fleet.booking.DeploymentVoucherRepository;
import com.limoz.fleet.common.util.Specifications;
import com.limoz.fleet.search.dto.SearchResult;
import com.limoz.fleet.security.Permissions;
import com.limoz.fleet.trip.Trip;
import com.limoz.fleet.trip.TripRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;

@Configuration
@RequiredArgsConstructor
public class OperationsSearchSources {

    private final BookingRepository bookingRepository;
    private final TripRepository tripRepository;
    private final DeploymentVoucherRepository voucherRepository;

    @Bean
    SearchSource bookingSearchSource() {
        return new SearchSource() {
            public String group() { return "bookings"; }
            public String requiredPermission() { return Permissions.BOOKING_READ; }
            public List<SearchResult> search(String q, int limit) {
                Specification<Booking> spec = Specifications.likeAny(q, "bookingNumber", "customer.name", "pickupLocation", "dropoffLocation", "contactName");
                return bookingRepository.findAll(spec, PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "startDate"))).stream()
                        .map(b -> new SearchResult("BOOKING", b.getId(), b.getBookingNumber(), b.getBookingNumber() + " · " + b.getCustomer().getName(),
                                b.getStartDate() + " – " + b.getEndDate() + " · " + b.getServiceType().name().replace('_', ' '),
                                b.getStatus().name(), "/bookings/" + b.getId()))
                        .toList();
            }
        };
    }

    @Bean
    SearchSource tripSearchSource() {
        return new SearchSource() {
            public String group() { return "trips"; }
            public String requiredPermission() { return Permissions.TRIP_READ; }
            public List<SearchResult> search(String q, int limit) {
                Specification<Trip> spec = Specifications.likeAny(q, "tripNumber", "vehicle.plateNumber", "driver.lastName", "driver.firstName", "origin", "destination");
                return tripRepository.findAll(spec, PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "scheduledStartAt"))).stream()
                        .map(t -> new SearchResult("TRIP", t.getId(), t.getTripNumber(), t.getTripNumber() + " · " + t.getVehicle().getPlateNumber(),
                                t.getOrigin() + " -> " + t.getDestination() + " · " + t.getDriver().getFullName(), t.getStatus().name(), "/trips/" + t.getId()))
                        .toList();
            }
        };
    }

    @Bean
    SearchSource voucherSearchSource() {
        return new SearchSource() {
            public String group() { return "vouchers"; }
            public String requiredPermission() { return Permissions.BOOKING_READ; }
            public List<SearchResult> search(String q, int limit) {
                Specification<DeploymentVoucher> spec = Specifications.likeAny(q, "voucherNumber", "vehicle.plateNumber", "customer.name", "booking.bookingNumber");
                return voucherRepository.findAll(spec, PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "voucherDate"))).stream()
                        .map(v -> new SearchResult("VOUCHER", v.getId(), v.getVoucherNumber(), v.getVoucherNumber(),
                                v.getCustomer().getName() + " · " + v.getVehicle().getPlateNumber() + " · " + v.getVoucherDate(),
                                v.getStatus().name(), "/vouchers/" + v.getId()))
                        .toList();
            }
        };
    }
}
