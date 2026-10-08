package com.limoz.fleet.vehicle.timeline;

import com.limoz.fleet.booking.DeploymentVoucher;
import com.limoz.fleet.booking.DeploymentVoucherRepository;
import com.limoz.fleet.common.util.Specifications;
import com.limoz.fleet.trip.Trip;
import com.limoz.fleet.trip.TripRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/** Trip and deployment events on the vehicle timeline. */
@Configuration
@RequiredArgsConstructor
public class OperationsTimelineSources {

    private final TripRepository tripRepository;
    private final DeploymentVoucherRepository voucherRepository;

    @Bean
    VehicleTimelineSource tripTimelineSource() {
        return (vehicleId, from, to) -> {
            Specification<Trip> spec = Specifications.and(Specifications.equal("vehicle.id", vehicleId),
                    (root, q, cb) -> cb.between(cb.coalesce(root.get("startedAt"), root.get("scheduledStartAt")), from, to));
            List<TimelineEntry> entries = new ArrayList<>();
            for (Trip t : tripRepository.findAll(spec, Sort.by("scheduledStartAt"))) {
                String route = t.getOrigin() + " -> " + t.getDestination() + " · " + t.getDriver().getFullName();
                if (t.getStartedAt() != null) {
                    entries.add(new TimelineEntry(t.getStartedAt(), "TRIP", "Trip started", t.getTripNumber() + " · " + route
                            + (t.getStartOdometerKm() == null ? "" : " · " + t.getStartOdometerKm() + " km"), "Trip", t.getId(), t.getTripNumber(), "/trips/" + t.getId()));
                } else {
                    entries.add(new TimelineEntry(t.getScheduledStartAt(), "TRIP", "Trip " + t.getStatus().name().toLowerCase(),
                            t.getTripNumber() + " · " + route, "Trip", t.getId(), t.getTripNumber(), "/trips/" + t.getId()));
                }
                if (t.getEndedAt() != null && !t.getEndedAt().isBefore(from) && !t.getEndedAt().isAfter(to)) {
                    entries.add(new TimelineEntry(t.getEndedAt(), "TRIP", "Trip completed", t.getTripNumber()
                            + (t.getDistanceKm() == null ? "" : " · " + t.getDistanceKm().stripTrailingZeros().toPlainString() + " km"),
                            "Trip", t.getId(), t.getTripNumber(), "/trips/" + t.getId()));
                }
            }
            return entries;
        };
    }

    @Bean
    VehicleTimelineSource deploymentTimelineSource() {
        return (vehicleId, from, to) -> {
            Specification<DeploymentVoucher> spec = Specifications.and(Specifications.equal("vehicle.id", vehicleId),
                    Specifications.instantBetween("createdAt", from, to));
            List<TimelineEntry> entries = new ArrayList<>();
            for (DeploymentVoucher v : voucherRepository.findAll(spec, Sort.by("createdAt"))) {
                entries.add(new TimelineEntry(v.getCreatedAt(), "DEPLOYMENT", "Deployed to " + v.getCustomer().getName(),
                        v.getVoucherNumber() + " · " + v.getBooking().getBookingNumber() + " · driver " + v.getDriver().getFullName(),
                        "DeploymentVoucher", v.getId(), v.getVoucherNumber(), "/vouchers/" + v.getId()));
                if (v.getReturnedAt() != null && !v.getReturnedAt().isBefore(from) && !v.getReturnedAt().isAfter(to)) {
                    entries.add(new TimelineEntry(v.getReturnedAt(), "DEPLOYMENT", "Returned from deployment", v.getVoucherNumber()
                            + " · " + v.getStatus().name().replace('_', ' '), "DeploymentVoucher", v.getId(), v.getVoucherNumber(), "/vouchers/" + v.getId()));
                }
            }
            return entries;
        };
    }
}
