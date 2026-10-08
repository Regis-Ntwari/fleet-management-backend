package com.limoz.fleet.reporting.service;

import com.limoz.fleet.reporting.domain.ReportParams;

import com.limoz.fleet.booking.domain.DeploymentVoucher;
import com.limoz.fleet.booking.repository.DeploymentVoucherRepository;
import com.limoz.fleet.booking.domain.VoucherStatus;
import com.limoz.fleet.common.util.DateRanges;
import com.limoz.fleet.common.util.Specifications;
import com.limoz.fleet.driver.domain.Driver;
import com.limoz.fleet.driver.repository.DriverRepository;
import com.limoz.fleet.reporting.export.domain.ReportTable;
import com.limoz.fleet.trip.domain.Trip;
import com.limoz.fleet.trip.repository.TripRepository;
import com.limoz.fleet.trip.domain.TripStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Trip, driver utilisation, deployment (voucher) and vehicle movement reports. */
@Configuration
@RequiredArgsConstructor
public class OperationsReports {

    private final TripRepository tripRepository;
    private final DriverRepository driverRepository;
    private final DeploymentVoucherRepository voucherRepository;
    private final JdbcClient jdbc;
    private final Clock clock;
    private final ZoneId zone;

    private DateRanges.InstantRange range(ReportParams p) {
        LocalDate to = p.toOr(LocalDate.now(clock));
        LocalDate from = p.fromOr(to.minusDays(29));
        return DateRanges.between(from, to, zone);
    }

    @Bean
    ReportProvider tripReport() {
        return new ReportProvider() {
            public String code() { return "trips"; }
            public String title() { return "Trip Report"; }
            public String description() { return "Trips in the period with distance, duration, customer and status."; }
            public ReportTable build(ReportParams p) {
                DateRanges.InstantRange r = range(p);
                Specification<Trip> spec = Specifications.and(
                        (root, q, cb) -> cb.between(cb.coalesce(root.get("startedAt"), root.get("scheduledStartAt")), r.from(), r.to()),
                        Specifications.equal("vehicle.id", p.vehicleId()),
                        Specifications.equal("driver.id", p.driverId()),
                        Specifications.equal("customer.id", p.customerId()),
                        Specifications.equal("vehicle.category.id", p.categoryId()),
                        p.status() == null ? null : Specifications.equal("status", TripStatus.valueOf(p.status().toUpperCase())));
                List<Trip> trips = tripRepository.findAll(spec, Sort.by("scheduledStartAt"));
                ReportTable.Builder b = ReportTable.builder(code(), title())
                        .meta("From", p.fromOr(LocalDate.now(clock).minusDays(29))).meta("To", p.toOr(LocalDate.now(clock)))
                        .text("number", "Trip").text("plate", "Vehicle").text("driver", "Driver").text("customer", "Customer")
                        .text("route", "Route").text("start", "Started").text("end", "Ended")
                        .number("distance", "Distance (km)").number("duration", "Duration (min)").text("status", "Status");
                BigDecimal totalKm = BigDecimal.ZERO;
                long totalMin = 0;
                for (Trip t : trips) {
                    b.row(t.getTripNumber(), t.getVehicle().getPlateNumber(), t.getDriver().getFullName(),
                            t.getCustomer() == null ? "-" : t.getCustomer().getName(), t.getOrigin() + " -> " + t.getDestination(),
                            t.getStartedAt() == null ? t.getScheduledStartAt() : t.getStartedAt(), t.getEndedAt(),
                            t.getDistanceKm(), t.getDurationMinutes(), t.getStatus());
                    if (t.getDistanceKm() != null) totalKm = totalKm.add(t.getDistanceKm());
                    if (t.getDurationMinutes() != null) totalMin += t.getDurationMinutes();
                }
                b.totals("Total", trips.size() + " trips", "", "", "", "", "", totalKm, totalMin, "");
                return b.build();
            }
        };
    }

    @Bean
    ReportProvider driverUtilizationReport() {
        return new ReportProvider() {
            public String code() { return "driver-utilization"; }
            public String title() { return "Driver Utilisation"; }
            public String description() { return "Trips, distance, driving time and active days per driver in the period."; }
            public ReportTable build(ReportParams p) {
                LocalDate to = p.toOr(LocalDate.now(clock));
                LocalDate from = p.fromOr(to.minusDays(29));
                DateRanges.InstantRange r = DateRanges.between(from, to, zone);
                long days = ChronoUnit.DAYS.between(from, to) + 1;
                List<Trip> trips = tripRepository.findAll(Specifications.and(
                        (root, q, cb) -> cb.between(cb.coalesce(root.get("startedAt"), root.get("scheduledStartAt")), r.from(), r.to()),
                        Specifications.equal("status", TripStatus.COMPLETED)));
                Map<Long, List<Trip>> byDriver = trips.stream().collect(Collectors.groupingBy(t -> t.getDriver().getId()));
                ReportTable.Builder b = ReportTable.builder(code(), title()).meta("From", from).meta("To", to)
                        .text("driver", "Driver").text("status", "Status").number("trips", "Trips").number("km", "Distance (km)")
                        .number("hours", "Driving hours").number("days", "Active days").number("utilisation", "Utilisation %");
                for (Driver d : driverRepository.findByArchivedFalseOrderByLastNameAscFirstNameAsc()) {
                    if (p.driverId() != null && !d.getId().equals(p.driverId())) continue;
                    List<Trip> mine = byDriver.getOrDefault(d.getId(), List.of());
                    BigDecimal km = mine.stream().map(t -> t.getDistanceKm() == null ? BigDecimal.ZERO : t.getDistanceKm()).reduce(BigDecimal.ZERO, BigDecimal::add);
                    long minutes = mine.stream().mapToLong(t -> t.getDurationMinutes() == null ? 0 : t.getDurationMinutes()).sum();
                    long activeDays = mine.stream().map(t -> DateRanges.toLocalDate(t.getStartedAt() == null ? t.getScheduledStartAt() : t.getStartedAt(), zone)).distinct().count();
                    b.row(d.getFullName(), d.getStatus(), mine.size(), km, BigDecimal.valueOf(minutes).divide(BigDecimal.valueOf(60), 1, RoundingMode.HALF_UP),
                            activeDays, BigDecimal.valueOf(activeDays * 100.0 / days).setScale(1, RoundingMode.HALF_UP));
                }
                return b.build();
            }
        };
    }

    @Bean
    ReportProvider deploymentReport() {
        return new ReportProvider() {
            public String code() { return "deployment"; }
            public String title() { return "Deployment Report"; }
            public String description() { return "Deployment vouchers: client, vehicle, driver, kilometres, days, amounts and status."; }
            public ReportTable build(ReportParams p) {
                LocalDate to = p.toOr(LocalDate.now(clock));
                LocalDate from = p.fromOr(to.minusDays(29));
                Specification<DeploymentVoucher> spec = Specifications.and(
                        Specifications.dateBetween("voucherDate", from, to),
                        Specifications.equal("vehicle.id", p.vehicleId()),
                        Specifications.equal("driver.id", p.driverId()),
                        Specifications.equal("customer.id", p.customerId()),
                        Specifications.equal("vehicle.category.id", p.categoryId()),
                        p.status() == null ? null : Specifications.equal("status", VoucherStatus.valueOf(p.status().toUpperCase())));
                ReportTable.Builder b = ReportTable.builder(code(), title()).meta("From", from).meta("To", to)
                        .text("voucher", "Voucher").text("date", "Date").text("booking", "Booking").text("client", "Client")
                        .text("plate", "Plate").text("category", "Category").text("driver", "Driver").text("destination", "Destination")
                        .number("startKm", "Start KM").number("endKm", "End KM").number("distance", "Distance").number("days", "Effective days")
                        .number("rate", "Day rate").number("institution", "Institution amount").number("owner", "Owner amount")
                        .number("fuel", "Fuel").number("net", "Net").number("missionDue", "Mission due").text("status", "Status");
                BigDecimal inst = BigDecimal.ZERO, owner = BigDecimal.ZERO, fuel = BigDecimal.ZERO, net = BigDecimal.ZERO, due = BigDecimal.ZERO;
                long km = 0;
                for (DeploymentVoucher v : voucherRepository.findAll(spec, Sort.by("voucherDate", "voucherNumber"))) {
                    long dist = v.getStartKm() != null && v.getEndKm() != null ? v.getEndKm() - v.getStartKm() : 0;
                    km += dist;
                    inst = inst.add(v.getInstitutionAmount()); owner = owner.add(v.getOwnerAmount()); fuel = fuel.add(v.getFuelAmount());
                    net = net.add(v.getNetAmount()); due = due.add(v.getMissionDueAmount());
                    b.row(v.getVoucherNumber(), v.getVoucherDate(), v.getBooking().getBookingNumber(), v.getCustomer().getName(),
                            v.getVehicle().getPlateNumber(), v.getVehicle().getCategory().getName(), v.getDriver().getFullName(), v.getDestination(),
                            v.getStartKm(), v.getEndKm(), dist, v.getEffectiveDays(), v.getDayRate(), v.getInstitutionAmount(), v.getOwnerAmount(),
                            v.getFuelAmount(), v.getNetAmount(), v.getMissionDueAmount(), v.getStatus());
                }
                b.totals("Total", "", "", "", "", "", "", "", "", "", km, "", "", inst, owner, fuel, net, due, "");
                return b.build();
            }
        };
    }

    @Bean
    ReportProvider vehicleMovementReport() {
        return new ReportProvider() {
            public String code() { return "vehicle-movement"; }
            public String title() { return "Vehicle Movement Report"; }
            public String description() { return "Daily movement per vehicle: distance, first/last movement, driving and idle time, flags (telematics or trips)."; }
            public ReportTable build(ReportParams p) {
                LocalDate to = p.toOr(LocalDate.now(clock));
                LocalDate from = p.fromOr(to.minusDays(6));
                ReportTable.Builder b = ReportTable.builder(code(), title()).meta("From", from).meta("To", to)
                        .text("date", "Date").text("plate", "Plate").text("category", "Category").number("distance", "Distance (km)")
                        .text("first", "First movement").text("last", "Last movement").number("driving", "Driving (min)").number("idle", "Idle (min)")
                        .number("maxSpeed", "Max speed").number("trips", "Trips").text("moved", "Moved").text("flags", "Flags").text("source", "Source");
                jdbc.sql("""
                        SELECT m.summary_date, v.plate_number, c.name, m.distance_km, m.first_movement_at, m.last_movement_at, m.driving_minutes,
                               m.idle_minutes, m.max_speed_kph, m.trips_count, m.moved, m.flags::text, m.data_source
                        FROM daily_movement_summaries m JOIN vehicles v ON v.id = m.vehicle_id JOIN vehicle_categories c ON c.id = v.category_id
                        WHERE m.summary_date BETWEEN :from::date AND :to::date
                          AND (:vehicleId::bigint IS NULL OR m.vehicle_id = :vehicleId::bigint)
                          AND (:categoryId::bigint IS NULL OR v.category_id = :categoryId::bigint)
                        ORDER BY m.summary_date DESC, v.plate_number""")
                        .param("from", from).param("to", to).param("vehicleId", p.vehicleId()).param("categoryId", p.categoryId())
                        .query((rs, i) -> {
                            Instant first = rs.getTimestamp(5) == null ? null : rs.getTimestamp(5).toInstant();
                            Instant last = rs.getTimestamp(6) == null ? null : rs.getTimestamp(6).toInstant();
                            b.row(rs.getDate(1).toLocalDate(), rs.getString(2), rs.getString(3), rs.getBigDecimal(4), first, last, rs.getInt(7),
                                    rs.getInt(8), rs.getBigDecimal(9), rs.getInt(10), rs.getBoolean(11) ? "Yes" : "No",
                                    rs.getString(12).replaceAll("[\\[\\]\"]", "").replace(",", ", "), rs.getString(13));
                            return 1;
                        }).list();
                return b.build();
            }
        };
    }
}
