package com.limoz.fleet.seed;

import com.limoz.fleet.booking.BookingService;
import com.limoz.fleet.booking.DispatchService;
import com.limoz.fleet.booking.PricingType;
import com.limoz.fleet.booking.ServiceType;
import com.limoz.fleet.booking.VoucherStatus;
import com.limoz.fleet.booking.dto.AssignSlotRequest;
import com.limoz.fleet.booking.dto.BookingCancelRequest;
import com.limoz.fleet.booking.dto.BookingLineRequest;
import com.limoz.fleet.booking.dto.BookingRequest;
import com.limoz.fleet.booking.dto.BookingResponse;
import com.limoz.fleet.booking.dto.BookingSlotResponse;
import com.limoz.fleet.booking.dto.DepartRequest;
import com.limoz.fleet.booking.dto.ReturnRequest;
import com.limoz.fleet.customer.CustomerService;
import com.limoz.fleet.customer.CustomerType;
import com.limoz.fleet.customer.dto.CustomerRequest;
import com.limoz.fleet.trip.TripService;
import com.limoz.fleet.trip.dto.TripCompleteRequest;
import com.limoz.fleet.trip.dto.TripRequest;
import com.limoz.fleet.trip.dto.TripResponse;
import com.limoz.fleet.trip.dto.TripStartRequest;
import com.limoz.fleet.vehicle.VehicleCategoryRepository;
import com.limoz.fleet.vehicle.VehicleService;
import com.limoz.fleet.vehicle.dto.VehicleResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Clients, 60 days of bookings with dispatch/vouchers, upcoming bookings and ad-hoc trips. */
@Slf4j
@Component
@RequiredArgsConstructor
public class OperationsSeedStep implements SeedStep {

    private final CustomerService customerService;
    private final BookingService bookingService;
    private final DispatchService dispatchService;
    private final TripService tripService;
    private final VehicleService vehicleService;
    private final VehicleCategoryRepository categoryRepository;

    private record C(String name, String tin, String contact, String phone, String city) {}

    @Override
    public int order() {
        return 20;
    }

    @Override
    public String name() {
        return "operations (clients, bookings, dispatch, vouchers, trips)";
    }

    @Override
    public void seed(SeedContext ctx) {
        seedCustomers(ctx);
        seedBookings(ctx);
        seedAdHocTrips(ctx);
    }

    private void seedCustomers(SeedContext ctx) {
        List<C> customers = List.of(
                new C("Bralirwa Ltd", "101 922 330", "Jean Bosco Habyarimana", "+250788300100", "Kigali"),
                new C("MTN Rwanda", "100 233 884", "Aline Mutesi", "+250788300200", "Kigali"),
                new C("University of Rwanda", "104 556 201", "Dr. Emmanuel Twagirayezu", "+250788300300", "Huye"),
                new C("Kigali City Council", "100 000 412", "Marie Claire Uwera", "+250788300400", "Kigali"),
                new C("Bank of Kigali", "100 118 770", "Patrick Rugamba", "+250788300500", "Kigali"),
                new C("Inyange Industries", "102 884 119", "Olive Kayitesi", "+250788300600", "Kigali"),
                new C("RwandAir", "100 001 234", "Yves Munyaneza", "+250788300700", "Kigali"),
                new C("420002 GARDEN FRESH Ltd", "102 938 471", "Josephine Mukarurangwa", "+250788300800", "Kigali"),
                new C("Akagera Safari Tours", "103 445 678", "Steven Mugisha", "+250788300900", "Kigali"),
                new C("Rwanda Development Board", "100 010 203", "Chantal Uwamahoro", "+250788301000", "Kigali"));
        int i = 0;
        for (C c : customers) {
            CustomerType type = c.name().contains("Council") || c.name().contains("Board") ? CustomerType.GOVERNMENT : CustomerType.CORPORATE;
            Long id = customerService.create(new CustomerRequest(null, c.name(), type, c.tin(), c.contact(),
                    c.contact().toLowerCase().replaceAll("[^a-z]", ".") + "@" + c.name().toLowerCase().replaceAll("[^a-z]", "") + ".rw",
                    c.phone(), "KN " + (i + 1) + " Rd", c.city(), "Rwanda", ctx.id("user:FLEET_MANAGER"), null, true, null)).id();
            ctx.add("customer", id);
            ctx.name("customer:" + c.name(), id);
            i++;
        }
    }

    private void seedBookings(SeedContext ctx) {
        Map<String, Long> cat = new HashMap<>();
        categoryRepository.findAll().forEach(c -> cat.put(c.getCode(), c.getId()));
        LocalDate today = ctx.today();
        // vehicles grouped by category code, used round-robin so no vehicle is double-booked
        Map<Long, List<Long>> vehiclesByCategory = new HashMap<>();
        Map<Long, Integer> cursor = new HashMap<>();
        for (Long vid : ctx.ids("vehicle")) {
            VehicleResponse v = vehicleService.get(vid);
            vehiclesByCategory.computeIfAbsent(v.categoryId(), k -> new ArrayList<>()).add(vid);
        }
        Map<Long, LocalDate> vehicleBusyUntil = new HashMap<>();
        Map<Long, LocalDate> driverBusyUntil = new HashMap<>();
        List<Long> drivers = ctx.ids("driver");

        record B(String customer, String catCode, int qty, PricingType pricing, int startOffset, int days, long unit, ServiceType type, String pickup, String dropoff) {}
        List<B> plan = List.of(
                new B("Bank of Kigali", "MINIBUS", 2, PricingType.MONTHLY, -58, 30, 2_400_000, ServiceType.STAFF_SHUTTLE, "Kigali - BK HQ", "Branches"),
                new B("MTN Rwanda", "MINIBUS", 2, PricingType.PER_TRIP, -52, 2, 390_000, ServiceType.AIRPORT_TRANSFER, "Kigali Airport", "MTN Nyarutarama"),
                new B("RwandAir", "COASTER", 1, PricingType.FULL_DAY, -47, 4, 260_000, ServiceType.STAFF_SHUTTLE, "Kigali Airport", "Crew hotel"),
                new B("University of Rwanda", "COACH", 1, PricingType.FULL_DAY, -40, 1, 450_000, ServiceType.FIELD_TRIP, "Huye campus", "Nyungwe"),
                new B("Inyange Industries", "CARGO_VAN", 1, PricingType.PER_TRIP, -36, 2, 780_000, ServiceType.CARGO, "Masaka plant", "Musanze depot"),
                new B("Akagera Safari Tours", "SAFARI", 2, PricingType.FULL_DAY, -33, 3, 200_000, ServiceType.SAFARI, "Kigali", "Akagera National Park"),
                new B("Rwanda Development Board", "LUX_SUV", 1, PricingType.FULL_DAY, -30, 2, 350_000, ServiceType.EVENT, "Kigali Convention Centre", "Kigali"),
                new B("Bralirwa Ltd", "COASTER", 2, PricingType.FULL_DAY, -26, 5, 800_000, ServiceType.STAFF_SHUTTLE, "Gisenyi brewery", "Kigali"),
                new B("Kigali City Council", "COACH", 2, PricingType.FULL_DAY, -22, 2, 1_280_000, ServiceType.EVENT, "City Hall", "Bugesera"),
                new B("MTN Rwanda", "LUX_VAN", 1, PricingType.HALF_DAY, -19, 1, 150_000, ServiceType.AIRPORT_TRANSFER, "Kigali Airport", "Serena Hotel"),
                new B("420002 GARDEN FRESH Ltd", "MINIBUS", 1, PricingType.FULL_DAY, -15, 3, 150_000, ServiceType.CHARTER, "Kigali", "Rubavu"),
                new B("University of Rwanda", "COACH", 1, PricingType.FULL_DAY, -12, 2, 450_000, ServiceType.FIELD_TRIP, "Huye campus", "Rubavu"),
                new B("Bralirwa Ltd", "MINIBUS", 1, PricingType.FULL_DAY, -9, 5, 640_000, ServiceType.STAFF_SHUTTLE, "Kigali", "Gisenyi"),
                new B("RwandAir", "SEDAN", 1, PricingType.FULL_DAY, -6, 3, 90_000, ServiceType.AIRPORT_TRANSFER, "Kigali Airport", "Kigali"),
                new B("Bank of Kigali", "LUX_SEDAN", 1, PricingType.FULL_DAY, -4, 2, 250_000, ServiceType.EVENT, "BK HQ", "Kigali"),
                new B("MTN Rwanda", "MINIBUS", 2, PricingType.PER_TRIP, -2, 2, 390_000, ServiceType.AIRPORT_TRANSFER, "Kigali Airport", "MTN Nyarutarama"),
                new B("Akagera Safari Tours", "SAFARI", 1, PricingType.FULL_DAY, -1, 3, 200_000, ServiceType.SAFARI, "Kigali", "Volcanoes National Park"),
                new B("Bralirwa Ltd", "COASTER", 2, PricingType.FULL_DAY, 2, 5, 800_000, ServiceType.STAFF_SHUTTLE, "Gisenyi brewery", "Kigali"),
                new B("MTN Rwanda", "MINIBUS", 2, PricingType.PER_TRIP, 3, 2, 390_000, ServiceType.AIRPORT_TRANSFER, "Kigali Airport", "MTN Nyarutarama"),
                new B("University of Rwanda", "COACH", 1, PricingType.FULL_DAY, 6, 1, 1_200_000, ServiceType.FIELD_TRIP, "Huye campus", "Kigali"),
                new B("Kigali City Council", "COACH", 2, PricingType.FULL_DAY, 9, 2, 1_280_000, ServiceType.EVENT, "City Hall", "Nyamata"),
                new B("Inyange Industries", "CARGO_VAN", 1, PricingType.PER_TRIP, 11, 2, 780_000, ServiceType.CARGO, "Masaka plant", "Rubavu depot"));

        int n = 0;
        for (B b : plan) {
            LocalDate start = today.plusDays(b.startOffset());
            LocalDate end = start.plusDays(b.days() - 1);
            boolean past = end.isBefore(today);
            boolean ongoing = !start.isAfter(today) && !end.isBefore(today);
            BookingRequest request = new BookingRequest(ctx.id("customer:" + b.customer()), null, null, null, null, b.type(),
                    b.pickup(), b.dropoff(), java.time.LocalTime.of(8, 0), java.time.LocalTime.of(17, 30), b.qty() * 10, "RWF", null,
                    "Seed booking", List.of(new BookingLineRequest(cat.get(b.catCode()), null, b.qty(), b.pricing(), start, end, BigDecimal.valueOf(b.unit()), null)),
                    n % 7 != 6);
            BookingResponse booking = bookingService.create(request);
            ctx.add("booking", booking.id());
            n++;
            if (n % 7 == 0) {
                // one in seven stays DRAFT; one cancelled further below
                continue;
            }
            if (n % 11 == 0) {
                bookingService.cancel(booking.id(), new BookingCancelRequest("Client postponed the event"));
                continue;
            }
            // assign vehicles + drivers to every slot when the booking is in the past, ongoing, or within the next 5 days
            boolean assign = past || ongoing || start.isBefore(today.plusDays(5));
            if (!assign) continue;
            for (BookingSlotResponse slot : booking.slots()) {
                Long vehicleId = pickVehicle(vehiclesByCategory.get(cat.get(b.catCode())), cursor, vehicleBusyUntil, start, cat.get(b.catCode()));
                Long driverId = pickDriver(drivers, driverBusyUntil, start, ctx);
                if (vehicleId == null || driverId == null) {
                    log.debug("Seed: no free vehicle/driver for {} slot {}", booking.bookingNumber(), slot.slotNumber());
                    continue;
                }
                try {
                    dispatchService.assignSlot(slot.id(), new AssignSlotRequest(vehicleId, driverId, null, false, null));
                } catch (RuntimeException ex) {
                    log.debug("Seed: could not assign {} to {}: {}", vehicleId, booking.bookingNumber(), ex.getMessage());
                    continue;
                }
                vehicleBusyUntil.put(vehicleId, end);
                driverBusyUntil.put(driverId, end);
                if (past || ongoing) {
                    Instant departed = ctx.at(start, 7, 30);
                    dispatchService.depart(slot.id(), new DepartRequest(null, departed, b.dropoff(), null));
                    if (past) {
                        long km = (long) b.days() * ctx.between(60, 260);
                        long out = vehicleService.get(vehicleId).odometerKm();
                        dispatchService.recordReturn(slot.id(), new ReturnRequest(out + km, ctx.at(end, 18, ctx.between(0, 59)),
                                BigDecimal.valueOf(km * 115L), BigDecimal.ZERO, null, VoucherStatus.RETURNED, "Returned on time", null));
                    }
                }
            }
        }
    }

    private Long pickVehicle(List<Long> candidates, Map<Long, Integer> cursor, Map<Long, LocalDate> busyUntil, LocalDate start, Long categoryId) {
        if (candidates == null || candidates.isEmpty()) return null;
        int startIndex = cursor.getOrDefault(categoryId, 0);
        for (int i = 0; i < candidates.size(); i++) {
            Long id = candidates.get((startIndex + i) % candidates.size());
            LocalDate busy = busyUntil.get(id);
            if (busy == null || busy.isBefore(start)) {
                cursor.put(categoryId, (startIndex + i + 1) % candidates.size());
                return id;
            }
        }
        return null;
    }

    private Long pickDriver(List<Long> drivers, Map<Long, LocalDate> busyUntil, LocalDate start, SeedContext ctx) {
        for (int i = 0; i < drivers.size(); i++) {
            Long id = drivers.get((ctx.between(0, drivers.size() - 1) + i) % drivers.size());
            LocalDate busy = busyUntil.get(id);
            if (busy == null || busy.isBefore(start)) return id;
        }
        return null;
    }

    private void seedAdHocTrips(SeedContext ctx) {
        LocalDate today = ctx.today();
        String[][] routes = {{"Kigali", "Huye", "Staff transport"}, {"Kigali", "Musanze", "Management visit"}, {"Kigali", "Rubavu", "Client delivery"},
                {"Kigali Airport", "Kigali", "VIP transfer"}, {"Kigali", "Nyamata", "Site inspection"}, {"Kigali", "Muhanga", "Spare parts collection"}};
        List<Long> drivers = ctx.ids("driver");
        List<Long> vehicles = ctx.ids("vehicle");
        int created = 0;
        for (int dayOffset = 45; dayOffset >= -3 && created < 60; dayOffset -= 2) {
            LocalDate day = today.minusDays(dayOffset);
            for (int k = 0; k < 2; k++) {
                Long vehicleId = vehicles.get((dayOffset + k * 7) % vehicles.size());
                Long driverId = drivers.get((dayOffset + k * 3) % drivers.size());
                String[] route = routes[(dayOffset + k) % routes.length];
                Instant startAt = ctx.at(day, 8 + k * 4, 15);
                try {
                    TripResponse trip = tripService.create(new TripRequest(vehicleId, driverId, ctx.pick(ctx.ids("customer")), route[0], route[1], null,
                            route[2], null, ctx.between(1, 12), startAt, startAt.plusSeconds(3600L * ctx.between(2, 7)), "Seed trip"));
                    created++;
                    if (day.isBefore(today)) {
                        long start = vehicleService.get(vehicleId).odometerKm();
                        tripService.start(trip.id(), new TripStartRequest(start, startAt));
                        long km = ctx.between(25, 240);
                        tripService.complete(trip.id(), new TripCompleteRequest(start + km, startAt.plusSeconds(3600L * ctx.between(2, 7) + 600),
                                BigDecimal.valueOf(km).multiply(new BigDecimal("0.14")).setScale(2, java.math.RoundingMode.HALF_UP),
                                BigDecimal.valueOf(ctx.between(60, 112)), null));
                    } else if (dayOffset < 0 && k == 0) {
                        tripService.dispatch(trip.id());
                    }
                } catch (RuntimeException ex) {
                    log.debug("Seed: skipped trip for vehicle {} on {}: {}", vehicleId, day, ex.getMessage());
                }
            }
        }
    }
}
