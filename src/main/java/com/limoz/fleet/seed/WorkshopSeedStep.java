package com.limoz.fleet.seed;

import com.limoz.fleet.fuel.FuelPaymentMethod;
import com.limoz.fleet.fuel.FuelService;
import com.limoz.fleet.fuel.dto.FuelTransactionRequest;
import com.limoz.fleet.maintenance.MaintenanceService;
import com.limoz.fleet.maintenance.MaintenanceScheduleService;
import com.limoz.fleet.maintenance.MaintenanceType;
import com.limoz.fleet.maintenance.Priority;
import com.limoz.fleet.maintenance.ServiceTypeRepository;
import com.limoz.fleet.maintenance.TaskStatus;
import com.limoz.fleet.maintenance.WorkshopService;
import com.limoz.fleet.maintenance.WorkshopType;
import com.limoz.fleet.maintenance.dto.MaintenanceCompleteRequest;
import com.limoz.fleet.maintenance.dto.MaintenanceDetailResponse;
import com.limoz.fleet.maintenance.dto.MaintenancePartRequest;
import com.limoz.fleet.maintenance.dto.MaintenancePaymentRequest;
import com.limoz.fleet.maintenance.dto.MaintenanceRequest;
import com.limoz.fleet.maintenance.dto.MaintenanceReviewRequest;
import com.limoz.fleet.maintenance.dto.MaintenanceTaskRequest;
import com.limoz.fleet.maintenance.dto.MaintenanceTaskStatusRequest;
import com.limoz.fleet.maintenance.dto.ScheduleRequest;
import com.limoz.fleet.maintenance.dto.WorkshopRequest;
import com.limoz.fleet.maintenance.inventory.SparePartService;
import com.limoz.fleet.maintenance.inventory.dto.SparePartRequest;
import com.limoz.fleet.vehicle.FuelType;
import com.limoz.fleet.vehicle.VehicleService;
import com.limoz.fleet.vehicle.dto.VehicleResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Fuel log for the last 60 days, workshops, spare parts stock, preventive schedules and maintenance jobs in every status. */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkshopSeedStep implements SeedStep {

    private final FuelService fuelService;
    private final WorkshopService workshopService;
    private final SparePartService sparePartService;
    private final ServiceTypeRepository serviceTypeRepository;
    private final MaintenanceScheduleService scheduleService;
    private final MaintenanceService maintenanceService;
    private final VehicleService vehicleService;

    @Override
    public int order() {
        return 30;
    }

    @Override
    public String name() {
        return "workshop (fuel, spare parts, schedules, maintenance jobs)";
    }

    @Override
    public void seed(SeedContext ctx) {
        seedFuel(ctx);
        seedWorkshopsAndParts(ctx);
        seedSchedules(ctx);
        seedMaintenanceJobs(ctx);
    }

    private void seedFuel(SeedContext ctx) {
        List<String> stations = List.of("SP Nyabugogo", "Engen Remera", "Kobil Kicukiro", "SP Kimironko", "Rubis Gisenyi", "Engen Huye");
        LocalDate today = ctx.today();
        for (Long vehicleId : ctx.ids("vehicle")) {
            VehicleResponse v = vehicleService.get(vehicleId);
            long odometer = Math.max(0, v.odometerKm() - 4000);
            int fills = ctx.between(3, 7);
            for (int i = fills; i >= 1; i--) {
                LocalDate day = today.minusDays((long) i * ctx.between(6, 11));
                if (!day.isBefore(today)) continue;
                long distance = ctx.between(250, 650);
                odometer += distance;
                if (odometer > v.odometerKm()) odometer = v.odometerKm() - (i - 1) * 50L;
                BigDecimal litres = BigDecimal.valueOf(distance).multiply(new BigDecimal(v.categoryName().contains("Coach") || v.categoryName().contains("Truck") ? "0.32" : v.categoryName().contains("Minibus") || v.categoryName().contains("Coaster") ? "0.16" : "0.11"))
                        .setScale(2, java.math.RoundingMode.HALF_UP);
                if (i == 1 && v.plateNumber().equals("RAD 177 J")) litres = litres.multiply(new BigDecimal("2.4")).setScale(2, java.math.RoundingMode.HALF_UP); // anomaly
                try {
                    fuelService.create(new FuelTransactionRequest(vehicleId, v.currentDriverId(), null, null, ctx.at(day, ctx.between(6, 19), ctx.between(0, 59)),
                            ctx.pick(stations), null, v.fuelType() == null ? FuelType.DIESEL : v.fuelType(), litres,
                            BigDecimal.valueOf(v.fuelType() == FuelType.PETROL ? 1_650 : 1_580), "RWF", odometer, null, true,
                            "RCPT-" + vehicleId + "-" + i, null, ctx.random().nextBoolean() ? FuelPaymentMethod.FUEL_CARD : FuelPaymentMethod.CASH, null));
                } catch (RuntimeException ex) {
                    log.debug("Seed: fuel transaction skipped for {}: {}", v.plateNumber(), ex.getMessage());
                }
            }
        }
    }

    private void seedWorkshopsAndParts(SeedContext ctx) {
        ctx.name("workshop:internal", workshopService.list().get(0).id());
        ctx.name("workshop:cfao", workshopService.create(new WorkshopRequest("CFAO Motors Rwanda", WorkshopType.EXTERNAL, "Service desk", "+250788200100", "service@cfao.rw", "Kigali Special Economic Zone", true)).id());
        ctx.name("workshop:akagera", workshopService.create(new WorkshopRequest("Akagera Motors", WorkshopType.EXTERNAL, "Workshop", "+250788200200", "workshop@akageramotors.rw", "Gikondo, Kigali", true)).id());
        Object[][] parts = {
                {"BP-1180", "Brake pads (front)", "Brakes", 45000, 8, 6}, {"BD-7740", "Brake discs", "Brakes", 95000, 4, 4},
                {"EO-5000", "Engine oil (5L)", "Lubricants", 38000, 12, 31}, {"OF-2201", "Oil filter", "Filters", 12000, 10, 42},
                {"AF-3310", "Air filter", "Filters", 18000, 8, 18}, {"FF-2210", "Fuel filter", "Filters", 22000, 6, 9},
                {"TB-9920", "Timing belt", "Engine", 85000, 4, 9}, {"CK-4455", "Clutch kit", "Transmission", 320000, 2, 3},
                {"BT-1212", "Battery 12V 100Ah", "Electrical", 156000, 4, 2}, {"RD-3030", "Radiator", "Cooling", 210000, 3, 5},
                {"TY-1955", "Tyre 195R15", "Tyres", 98000, 8, 14}, {"TY-1100", "Tyre 11R22.5", "Tyres", 285000, 6, 4},
                {"WB-6600", "Wiper blades (pair)", "Body", 9000, 6, 20}, {"HL-7100", "Headlamp bulb H4", "Electrical", 4500, 10, 25},
                {"CL-0500", "Coolant (5L)", "Lubricants", 15000, 6, 11}};
        for (Object[] p : parts) {
            Long id = sparePartService.create(new SparePartRequest((String) p[0], (String) p[1], (String) p[2], "pcs", BigDecimal.valueOf((int) p[3]),
                    ctx.pick(List.of("CFAO Motors Rwanda", "Akagera Motors", "Kigali Auto Parts")), (int) p[4], (int) p[5], "Main store", true, null)).id();
            ctx.name("part:" + p[0], id);
            ctx.add("part", id);
        }
    }

    private void seedSchedules(SeedContext ctx) {
        Map<String, Long> types = Map.of(
                "ENGINE_OIL", serviceTypeRepository.findByCodeIgnoreCase("ENGINE_OIL").orElseThrow().getId(),
                "GENERAL_SERVICE", serviceTypeRepository.findByCodeIgnoreCase("GENERAL_SERVICE").orElseThrow().getId(),
                "BRAKE_INSPECTION", serviceTypeRepository.findByCodeIgnoreCase("BRAKE_INSPECTION").orElseThrow().getId(),
                "TYRES", serviceTypeRepository.findByCodeIgnoreCase("TYRES").orElseThrow().getId());
        int i = 0;
        for (Long vehicleId : ctx.ids("vehicle")) {
            VehicleResponse v = vehicleService.get(vehicleId);
            // spread last-service readings so that some schedules are OK, some DUE_SOON and some OVERDUE
            long lastOil = v.odometerKm() - switch (i % 5) { case 0 -> 5200; case 1 -> 4700; case 2 -> 2000; case 3 -> 4100; default -> 900; };
            scheduleService.create(new ScheduleRequest(vehicleId, types.get("ENGINE_OIL"), 5000, 180, Math.max(0, lastOil), ctx.today().minusDays(ctx.between(20, 170)), true, null));
            scheduleService.create(new ScheduleRequest(vehicleId, types.get("GENERAL_SERVICE"), 10000, 365, Math.max(0, v.odometerKm() - ctx.between(1000, 9500)), ctx.today().minusDays(ctx.between(30, 300)), true, null));
            if (i % 2 == 0) {
                scheduleService.create(new ScheduleRequest(vehicleId, types.get("BRAKE_INSPECTION"), 10000, 180, Math.max(0, v.odometerKm() - ctx.between(500, 9800)), ctx.today().minusDays(ctx.between(10, 170)), true, null));
            }
            if (i % 3 == 0) {
                scheduleService.create(new ScheduleRequest(vehicleId, types.get("TYRES"), 40000, 730, Math.max(0, v.odometerKm() - ctx.between(5000, 39000)), ctx.today().minusDays(ctx.between(60, 700)), true, null));
            }
            i++;
        }
        scheduleService.refreshAll();
    }

    private void seedMaintenanceJobs(SeedContext ctx) {
        Long technician = ctx.id("user:TECHNICIAN");
        Long manager = ctx.id("user:WORKSHOP_MANAGER");
        Long oilType = serviceTypeRepository.findByCodeIgnoreCase("ENGINE_OIL").orElseThrow().getId();
        Long brakeType = serviceTypeRepository.findByCodeIgnoreCase("BRAKE_INSPECTION").orElseThrow().getId();
        record J(String plate, int daysAgo, String complaint, MaintenanceType type, Priority priority, String workshop, String outcome) {}
        List<J> jobs = List.of(
                new J("RAD 744 F", 48, "Routine 5,000 km service", MaintenanceType.PREVENTIVE, Priority.LOW, "internal", "RELEASED"),
                new J("RAD 855 G", 41, "Brake pedal soft, grinding noise front left", MaintenanceType.CORRECTIVE, Priority.HIGH, "internal", "RELEASED"),
                new J("RAE 103 A", 35, "Air conditioning not cooling", MaintenanceType.CORRECTIVE, Priority.MEDIUM, "cfao", "RELEASED"),
                new J("RAD 288 K", 27, "Routine service + tyre rotation", MaintenanceType.BOTH, Priority.LOW, "internal", "COMPLETED"),
                new J("RAD 966 H", 20, "Battery dead, does not start", MaintenanceType.CORRECTIVE, Priority.HIGH, "akagera", "RELEASED"),
                new J("RAD 410 M", 14, "Clutch slipping under load", MaintenanceType.CORRECTIVE, Priority.HIGH, "internal", "CANCELLED"),
                new J("RAD 309 A", 6, "Engine overheating on Kigali-Rubavu descent, coolant leak", MaintenanceType.CORRECTIVE, Priority.CRITICAL, "internal", "WAITING_FOR_PARTS"),
                new J("RAD 177 J", 4, "Excessive fuel consumption, black smoke", MaintenanceType.INSPECTION, Priority.MEDIUM, "internal", "IN_PROGRESS"),
                new J("RAE 109 A", 2, "Pop-up roof hinge broken, rear shock absorber leaking", MaintenanceType.CORRECTIVE, Priority.MEDIUM, "internal", "APPROVED"),
                new J("RAG 337 B", 1, "Intake: 84,210 km service, check steering play", MaintenanceType.BOTH, Priority.MEDIUM, "internal", "INSPECTION"),
                new J("RAE 105 A", 0, "Warning light on dashboard (check engine)", MaintenanceType.INSPECTION, Priority.LOW, "internal", "REPORTED"));
        for (J j : jobs) {
            Long vehicleId = ctx.id("vehicle:" + j.plate());
            VehicleResponse v = vehicleService.get(vehicleId);
            Instant reported = ctx.at(ctx.today().minusDays(j.daysAgo()), 8, 30);
            boolean intake = j.plate().equals("RAG 337 B") || j.outcome().equals("RELEASED");
            MaintenanceRequest request = new MaintenanceRequest(vehicleId, reported, null, v.ownerName(), v.department(), v.currentDriverId(), null, null,
                    j.complaint(), intake ? "Body clean, fuel 1/2, spare wheel and tools present" : null, j.type(), j.priority(), ctx.id("workshop:" + j.workshop()),
                    j.workshop().equals("internal") ? technician : null, j.workshop().equals("internal") ? null : "Vendor technician", manager, null,
                    v.odometerKm(), ctx.today().minusDays(j.daysAgo()).plusDays(ctx.between(2, 6)), BigDecimal.valueOf(ctx.between(20, 120) * 1000L), BigDecimal.ZERO, null);
            try {
                MaintenanceDetailResponse job = intake ? maintenanceService.intake(request) : maintenanceService.report(request);
                Long id = job.job().id();
                ctx.add("maintenance", id);
                if (j.outcome().equals("REPORTED")) continue;
                List<MaintenancePartRequest> parts = switch (j.type()) {
                    case PREVENTIVE, BOTH -> List.of(part(ctx, "EO-5000", 1), part(ctx, "OF-2201", 1), part(ctx, "AF-3310", 1));
                    case CORRECTIVE -> j.complaint().contains("Brake") ? List.of(part(ctx, "BP-1180", 2), part(ctx, "BD-7740", 2))
                            : j.complaint().contains("Battery") ? List.of(part(ctx, "BT-1212", 1))
                            : j.complaint().contains("overheating") ? List.of(part(ctx, "RD-3030", 1), part(ctx, "CL-0500", 2))
                            : j.complaint().contains("Clutch") ? List.of(part(ctx, "CK-4455", 1)) : List.of();
                    default -> List.of();
                };
                maintenanceService.submitReview(id, new MaintenanceReviewRequest("Diagnosis for: " + j.complaint(), List.of("Worn components", "Noise under load"),
                        "Replace affected parts and road test", "Bay 2, 4 h estimated", ctx.today().minusDays(j.daysAgo()), ctx.today().minusDays(j.daysAgo()).plusDays(3), parts));
                if (j.outcome().equals("INSPECTION")) continue;
                maintenanceService.approve(id);
                maintenanceService.addTask(id, new MaintenanceTaskRequest(j.type() == MaintenanceType.PREVENTIVE || j.type() == MaintenanceType.BOTH ? oilType : null,
                        "Perform work: " + j.complaint(), new BigDecimal("3.5"), BigDecimal.valueOf(35000), null, 1));
                maintenanceService.addTask(id, new MaintenanceTaskRequest(j.complaint().contains("Brake") ? brakeType : null, "Road test and final inspection", BigDecimal.ONE, BigDecimal.valueOf(10000), null, 2));
                if (j.outcome().equals("APPROVED")) continue;
                maintenanceService.start(id);
                MaintenanceDetailResponse detail = maintenanceService.get(id);
                for (var part : detail.parts()) {
                    try {
                        maintenanceService.approvePart(id, part.id());
                    } catch (RuntimeException ex) {
                        log.debug("Seed: part approval skipped ({})", ex.getMessage());
                    }
                }
                if (j.outcome().equals("IN_PROGRESS")) continue;
                if (j.outcome().equals("WAITING_FOR_PARTS")) {
                    maintenanceService.waitForParts(id, "Radiator ordered from CFAO, ETA 3 days");
                    continue;
                }
                if (j.outcome().equals("CANCELLED")) {
                    maintenanceService.cancel(id, new com.limoz.fleet.maintenance.dto.MaintenanceCancelRequest("Vehicle sold - job no longer required"));
                    continue;
                }
                for (var task : maintenanceService.get(id).tasks()) {
                    maintenanceService.setTaskStatus(id, task.id(), new MaintenanceTaskStatusRequest(TaskStatus.DONE, null));
                }
                maintenanceService.complete(id, new MaintenanceCompleteRequest("Work completed as per review: " + j.complaint(), null, BigDecimal.valueOf(ctx.between(0, 15) * 1000L), null));
                if (j.outcome().equals("RELEASED")) {
                    maintenanceService.release(id);
                    maintenanceService.recordPayment(id, new MaintenancePaymentRequest(maintenanceService.get(id).job().totalCost(), "BANK_TRANSFER", "PAY-SEED-" + id, reported.plusSeconds(86400L * 5)));
                }
            } catch (RuntimeException ex) {
                log.warn("Seed: maintenance job for {} stopped early: {}", j.plate(), ex.getMessage());
            }
        }
    }

    private MaintenancePartRequest part(SeedContext ctx, String number, int qty) {
        return new MaintenancePartRequest(ctx.id("part:" + number), null, null, qty, null);
    }
}
