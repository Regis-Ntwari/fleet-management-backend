package com.limoz.fleet.seed.service;

import com.limoz.fleet.booking.domain.BookingStatus;
import com.limoz.fleet.booking.dto.BookingFilter;
import com.limoz.fleet.booking.dto.BookingSummary;
import com.limoz.fleet.booking.service.BookingService;
import com.limoz.fleet.customer.dto.CommitmentRequest;
import com.limoz.fleet.customer.dto.PurchaseOrderRequest;
import com.limoz.fleet.customer.service.CommitmentService;
import com.limoz.fleet.customer.service.PurchaseOrderService;
import com.limoz.fleet.finance.domain.PaymentDirection;
import com.limoz.fleet.finance.domain.PaymentMethod;
import com.limoz.fleet.finance.domain.PaymentTerms;
import com.limoz.fleet.finance.dto.ExpenseRequest;
import com.limoz.fleet.finance.dto.ExpenseResponse;
import com.limoz.fleet.finance.dto.InvoiceFromBookingRequest;
import com.limoz.fleet.finance.dto.InvoiceResponse;
import com.limoz.fleet.finance.dto.PaymentRequest;
import com.limoz.fleet.finance.dto.SettlementRequest;
import com.limoz.fleet.finance.repository.ExpenseCategoryRepository;
import com.limoz.fleet.finance.service.ExpenseService;
import com.limoz.fleet.finance.service.InvoiceService;
import com.limoz.fleet.finance.service.PaymentService;
import com.limoz.fleet.incident.domain.IncidentSeverity;
import com.limoz.fleet.incident.domain.IncidentType;
import com.limoz.fleet.incident.dto.IncidentRequest;
import com.limoz.fleet.incident.dto.IncidentResponse;
import com.limoz.fleet.incident.dto.TrafficFineRequest;
import com.limoz.fleet.incident.dto.TrafficFineResponse;
import com.limoz.fleet.incident.service.IncidentService;
import com.limoz.fleet.incident.service.TrafficFineService;
import com.limoz.fleet.notification.alert.service.AlertService;
import com.limoz.fleet.seed.domain.SeedContext;
import com.limoz.fleet.seed.domain.SeedStep;
import com.limoz.fleet.telematics.domain.FuelSensorStatus;
import com.limoz.fleet.telematics.dto.DeviceRequest;
import com.limoz.fleet.telematics.dto.PositionInput;
import com.limoz.fleet.telematics.movement.service.DailyMovementService;
import com.limoz.fleet.telematics.service.PositionIngestService;
import com.limoz.fleet.telematics.service.TelematicsDeviceService;
import com.limoz.fleet.vehicle.dto.VehicleResponse;
import com.limoz.fleet.vehicle.service.VehicleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Commitments, LPOs, incidents, fines, invoices, payments, expenses, GPS devices/positions, movement analysis and alerts. */
@Slf4j
@Component
@RequiredArgsConstructor
public class ComplianceFinanceSeedStep implements SeedStep {

    private final CommitmentService commitmentService;
    private final PurchaseOrderService purchaseOrderService;
    private final BookingService bookingService;
    private final IncidentService incidentService;
    private final TrafficFineService fineService;
    private final InvoiceService invoiceService;
    private final PaymentService paymentService;
    private final ExpenseService expenseService;
    private final ExpenseCategoryRepository expenseCategoryRepository;
    private final TelematicsDeviceService deviceService;
    private final PositionIngestService ingestService;
    private final DailyMovementService movementService;
    private final AlertService alertService;
    private final VehicleService vehicleService;

    @Override
    public int order() {
        return 40;
    }

    @Override
    public String name() {
        return "compliance & finance (contracts, LPOs, incidents, fines, invoices, payments, expenses, GPS, alerts)";
    }

    @Override
    public void seed(SeedContext ctx) {
        run("contracts", () -> seedContracts(ctx));
        run("incidents", () -> seedIncidents(ctx));
        run("fines", () -> seedFines(ctx));
        run("invoices", () -> seedInvoices(ctx));
        run("expenses", () -> seedExpenses(ctx));
        run("telematics", () -> seedTelematics(ctx));
        run("alerts", () -> alertService.runScan());
    }

    private void run(String what, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException ex) {
            log.warn("Seed: {} stopped early: {}", what, ex.getMessage());
        }
    }

    private void seedContracts(SeedContext ctx) {
        LocalDate today = ctx.today();
        Object[][] contracts = {
                {"Bralirwa Ltd", "Staff Shuttle 2026", today.withDayOfYear(1), today.withDayOfYear(1).plusYears(1).minusDays(1), 28_800_000L, "RWF", true},
                {"Bank of Kigali", "Branch Staff Transport", today.withDayOfYear(1), today.withDayOfYear(1).plusYears(1).minusDays(1), 24_000_000L, "RWF", true},
                {"MTN Rwanda", "Roadshow Logistics", today.minusMonths(1).withDayOfMonth(1), today.plusMonths(3), 9_400_000L, "RWF", true},
                {"MTN Rwanda", "Airport Transfers", today.minusMonths(2).withDayOfMonth(1), today.plusDays(12), 6_000_000L, "RWF", true},
                {"University of Rwanda", "Field Trips 2026", today.minusMonths(4), today.plusMonths(5), 4_800_000L, "RWF", true},
                {"RwandAir", "Crew Shuttle", today.withDayOfYear(1), today.withDayOfYear(1).plusYears(1).minusDays(1), 36_000L, "USD", true},
                {"Kigali City Council", "Conference Season", today.plusMonths(1), today.plusMonths(4), 3_200_000L, "RWF", false}};
        for (Object[] c : contracts) {
            Long id = commitmentService.create(new CommitmentRequest(ctx.id("customer:" + c[0]), (String) c[1], (LocalDate) c[2], (LocalDate) c[3],
                    BigDecimal.valueOf((long) c[4]), (String) c[5], null, null)).id();
            if ((boolean) c[6]) commitmentService.activate(id);
            ctx.add("commitment", id);
            ctx.name("commitment:" + c[1], id);
        }
        List<BookingSummary> bookings = bookingService.search(new BookingFilter(null, null, null, null, null, null, null), PageRequest.of(0, 50)).content();
        int n = 0;
        for (BookingSummary b : bookings) {
            if (b.status() == BookingStatus.CANCELLED || b.status() == BookingStatus.DRAFT || n++ % 2 == 1) continue;
            Long commitment = switch (b.customerName()) {
                case "Bralirwa Ltd" -> ctx.id("commitment:Staff Shuttle 2026");
                case "Bank of Kigali" -> ctx.id("commitment:Branch Staff Transport");
                case "MTN Rwanda" -> ctx.id("commitment:Airport Transfers");
                case "University of Rwanda" -> ctx.id("commitment:Field Trips 2026");
                default -> null;
            };
            try {
                Long po = purchaseOrderService.create(new PurchaseOrderRequest(b.customerId(), commitment, b.id(), b.startDate().minusDays(5), b.startDate().plusDays(25),
                        b.startDate().minusDays(3), b.totalAmount(), b.currency(), null, "LPO for " + b.bookingNumber())).id();
                ctx.add("purchaseOrder", po);
            } catch (RuntimeException ex) {
                log.debug("Seed: LPO skipped for {}: {}", b.bookingNumber(), ex.getMessage());
            }
        }
    }

    private void seedIncidents(SeedContext ctx) {
        Object[][] incidents = {
                {"RAD 309 A", 29, IncidentType.ACCIDENT, IncidentSeverity.MINOR, "Nyabugogo roundabout", "Rear bumper scraped while reversing out of the bay", "CLOSED"},
                {"RAD 521 D", 24, IncidentType.ACCIDENT, IncidentSeverity.MODERATE, "RN1 near Muhanga", "Side collision with a motorcycle, no injuries, police report filed", "UNDER_INVESTIGATION"},
                {"RAD 112 B", 15, IncidentType.DAMAGE, IncidentSeverity.MINOR, "Kicukiro junction", "Windscreen chipped by a stone", "CLOSED"},
                {"RAD 855 G", 8, IncidentType.ACCIDENT, IncidentSeverity.MAJOR, "RN4 near Rubavu", "Brake failure on descent, coach hit the embankment; 2 passengers with light injuries", "OPEN"},
                {"RAD 966 H", 5, IncidentType.BREAKDOWN, IncidentSeverity.MODERATE, "Musanze depot", "Engine stalled, towed to garage", "RESOLVED"},
                {"RAD 177 J", 3, IncidentType.FUEL_ANOMALY, IncidentSeverity.MINOR, "Nyabugogo yard", "Fuel level dropped 40 L overnight", "UNDER_INVESTIGATION"},
                {"RAE 104 A", 1, IncidentType.THEFT, IncidentSeverity.MODERATE, "Remera parking", "Spare wheel and tools stolen", "OPEN"}};
        for (Object[] i : incidents) {
            VehicleResponse v = vehicleService.get(ctx.id("vehicle:" + i[0]));
            IncidentResponse created = incidentService.report(new IncidentRequest(v.id(), v.currentDriverId(), null, ctx.at(ctx.today().minusDays((int) i[1]), ctx.between(6, 20), 15),
                    (String) i[4], null, null, (IncidentType) i[2], (IncidentSeverity) i[3], (String) i[5], i[2] == IncidentType.ACCIDENT, ((String) i[5]).contains("injuries"),
                    i[2] == IncidentType.ACCIDENT ? "RNP/" + ctx.between(1000, 9999) + "/2026" : null, i[2] == IncidentType.FUEL_ANOMALY ? new BigDecimal("40") : null,
                    BigDecimal.valueOf(ctx.between(50, 900) * 1000L), null));
            ctx.add("incident", created.id());
            String outcome = (String) i[6];
            if (outcome.equals("OPEN")) continue;
            incidentService.startInvestigation(created.id(), "Investigation opened by the fleet manager");
            if (outcome.equals("UNDER_INVESTIGATION")) continue;
            incidentService.resolve(created.id(), "Vehicle repaired and driver re-briefed on safe driving", "Resolved after workshop inspection");
            if (outcome.equals("CLOSED")) incidentService.close(created.id(), "Closed - insurance claim settled");
        }
    }

    private void seedFines(SeedContext ctx) {
        Object[][] fines = {{"RAD 521 D", 7, "Over-speeding (Kigali-Rubavu)", 25000, "UNPAID"}, {"RAD 408 C", 12, "Illegal parking", 10000, "PAID"},
                {"RAD 112 B", 17, "No seatbelt", 25000, "DISPUTED"}, {"RAD 855 G", 21, "Over-speeding", 25000, "PAID"},
                {"RAD 744 F", 27, "Expired sticker", 15000, "UNPAID"}, {"RAE 103 A", 2, "Use of phone while driving", 25000, "UNPAID"}};
        for (Object[] f : fines) {
            VehicleResponse v = vehicleService.get(ctx.id("vehicle:" + f[0]));
            Instant issued = ctx.at(ctx.today().minusDays((int) f[1]), ctx.between(7, 19), 0);
            TrafficFineResponse fine = fineService.record(new TrafficFineRequest(v.id(), v.currentDriverId(), null, "RNP-" + ctx.between(100000, 999999), issued,
                    "Kigali", (String) f[2], BigDecimal.valueOf((int) f[3]), "RWF", ctx.today().minusDays((int) f[1]).plusDays(14), ctx.random().nextBoolean(), null, null));
            if (f[4].equals("PAID")) {
                fineService.pay(fine.id(), new SettlementRequest(PaymentMethod.MOBILE_MONEY, issued.plusSeconds(86400L * 3), null, "MOMO-" + ctx.between(100000, 999999), null, null));
            } else if (f[4].equals("DISPUTED")) {
                fineService.dispute(fine.id(), "Driver was wearing the seatbelt; dashcam footage submitted");
            }
        }
    }

    private void seedInvoices(SeedContext ctx) {
        List<BookingSummary> billable = new ArrayList<>();
        for (BookingStatus status : List.of(BookingStatus.READY_FOR_BILLING, BookingStatus.COMPLETED)) {
            billable.addAll(bookingService.search(new BookingFilter(null, List.of(status), null, null, null, null, null), PageRequest.of(0, 50)).content());
        }
        int n = 0;
        for (BookingSummary b : billable) {
            if (n++ % 4 == 3) continue; // leave some bookings in the "ready to bill" queue
            try {
                InvoiceResponse invoice = invoiceService.createFromBooking(b.id(), new InvoiceFromBookingRequest(PaymentTerms.NET_30, BigDecimal.ZERO, b.endDate().plusDays(1), null));
                ctx.add("invoice", invoice.id());
                if (n % 5 == 0) continue; // draft
                invoiceService.issue(invoice.id());
                if (n % 3 == 0) continue; // issued, unpaid (some become overdue)
                BigDecimal amount = n % 2 == 0 ? invoice.totalAmount() : invoice.totalAmount().divide(BigDecimal.valueOf(2), 2, java.math.RoundingMode.HALF_UP);
                paymentService.record(new PaymentRequest(PaymentDirection.IN, null, null, invoice.id(), null, null, null,
                        n % 2 == 0 ? PaymentMethod.BANK_TRANSFER : PaymentMethod.MOBILE_MONEY, amount, invoice.currency(),
                        ctx.at(b.endDate().plusDays(ctx.between(3, 20)), 10, 0), "TRX-" + ctx.between(100000, 999999), null, null));
            } catch (RuntimeException ex) {
                log.debug("Seed: invoice skipped for {}: {}", b.bookingNumber(), ex.getMessage());
            }
        }
        invoiceService.refreshStatuses();
    }

    private void seedExpenses(SeedContext ctx) {
        var categories = expenseCategoryRepository.findAll();
        Object[][] expenses = {{"TOLLS_PARKING", "Kigali-Huye return tolls and parking", 18000, 1, "RAD 408 C", "APPROVED"},
                {"CLEANING", "Fleet wash (6 vehicles)", 42000, 2, null, "APPROVED"}, {"OFFICE", "Printer toner & stationery", 65000, 2, null, "PENDING"},
                {"DRIVER_ALLOWANCE", "Per-diem · Rubavu overnight", 30000, 4, "RAD 521 D", "PAID"}, {"PERMITS", "RURA route permit renewal", 120000, 5, "RAD 112 B", "PENDING"},
                {"REPAIRS", "Tyre puncture roadside fix", 8000, 7, "RAD 966 H", "REJECTED"}, {"DRIVER_ALLOWANCE", "Per-diem · Akagera safari (3 days)", 90000, 9, "RAE 108 A", "PAID"},
                {"TYRES", "Two tyres 195R15 fitted on the road", 196000, 14, "RAD 633 E", "APPROVED"}, {"INSURANCE", "Insurance excess - RAD 521 D claim", 250000, 20, "RAD 521 D", "PAID"}};
        for (Object[] e : expenses) {
            Long category = categories.stream().filter(c -> c.getCode().equals(e[0])).findFirst().orElseThrow().getId();
            Long vehicleId = e[4] == null ? null : ctx.id("vehicle:" + e[4]);
            ExpenseResponse exp = expenseService.submit(new ExpenseRequest(category, (String) e[1], BigDecimal.valueOf((int) e[2]), "RWF", ctx.today().minusDays((int) e[3]),
                    vehicleId, null, null, null, null, null));
            switch ((String) e[5]) {
                case "APPROVED" -> expenseService.approve(exp.id());
                case "REJECTED" -> expenseService.reject(exp.id(), "Not covered by policy - roadside repairs go through the workshop");
                case "PAID" -> {
                    expenseService.approve(exp.id());
                    expenseService.markPaid(exp.id(), new SettlementRequest(PaymentMethod.CASH, ctx.at(ctx.today().minusDays((int) e[3]).plusDays(1), 14, 0), null, null, null, null));
                }
                default -> { }
            }
        }
    }

    private void seedTelematics(SeedContext ctx) {
        List<Long> vehicles = ctx.ids("vehicle");
        List<PositionInput> positions = new ArrayList<>();
        LocalDate today = ctx.today();
        for (int i = 0; i < vehicles.size(); i++) {
            Long vehicleId = vehicles.get(i);
            if (i % 4 == 3) continue; // a quarter of the fleet has no tracker yet
            deviceService.register(new DeviceRequest(vehicleId, "manual", "GPS-" + (1000 + i), "+2507301" + String.format("%05d", i), today.minusMonths(ctx.between(2, 18)),
                    i % 9 == 4 ? FuelSensorStatus.FAULTY : i % 2 == 0 ? FuelSensorStatus.OK : FuelSensorStatus.NOT_INSTALLED, null));
            if (i % 8 == 6) continue; // device registered but silent -> NO_SIGNAL / offline alerts
            VehicleResponse v = vehicleService.get(vehicleId);
            BigDecimal odometer = BigDecimal.valueOf(v.odometerKm());
            // yesterday and today: a trip from Kigali towards Huye and back, sampled every 15 minutes
            for (int day = 1; day >= 0; day--) {
                LocalDate d = today.minusDays(day);
                boolean moves = !(i % 5 == 2 && day == 1);
                double lat = -1.9441, lon = 30.0619;
                int samples = day == 0 ? 20 : 40;
                for (int s = 0; s < samples; s++) {
                    Instant at = ctx.at(d, 6, 0).plusSeconds(900L * s);
                    boolean moving = moves && s > 2 && s < samples - 3;
                    BigDecimal speed = moving ? BigDecimal.valueOf(ctx.between(35, i % 7 == 0 ? 105 : 80)) : BigDecimal.ZERO;
                    if (moving) {
                        lat -= 0.004; lon -= 0.001;
                        odometer = odometer.add(speed.multiply(new BigDecimal("0.25")));
                    }
                    positions.add(new PositionInput(vehicleId, null, null, at, BigDecimal.valueOf(lat).setScale(6, java.math.RoundingMode.HALF_UP),
                            BigDecimal.valueOf(lon).setScale(6, java.math.RoundingMode.HALF_UP), speed, BigDecimal.valueOf(ctx.between(0, 359)),
                            odometer.setScale(1, java.math.RoundingMode.HALF_UP), moving || s % 7 == 0, new BigDecimal("12.6"), null));
                }
            }
        }
        if (!positions.isEmpty()) {
            ingestService.ingest(positions, "seed");
        }
        deviceService.refreshGpsStatuses();
        movementService.computeFor(today.minusDays(1));
        movementService.computeFor(today);
        for (int d = 2; d <= 7; d++) {
            movementService.computeFor(today.minusDays(d)); // trips-based summaries for earlier days
        }
    }
}
