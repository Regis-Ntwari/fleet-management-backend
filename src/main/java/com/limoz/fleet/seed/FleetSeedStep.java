package com.limoz.fleet.seed;

import com.limoz.fleet.assignment.AssignmentService;
import com.limoz.fleet.assignment.dto.AssignmentRequest;
import com.limoz.fleet.document.DocumentService;
import com.limoz.fleet.document.DocumentTypeRepository;
import com.limoz.fleet.document.dto.DocumentRequest;
import com.limoz.fleet.driver.DriverService;
import com.limoz.fleet.driver.EmploymentStatus;
import com.limoz.fleet.driver.dto.DriverRequest;
import com.limoz.fleet.driver.dto.DriverResponse;
import com.limoz.fleet.security.Roles;
import com.limoz.fleet.user.UserService;
import com.limoz.fleet.user.dto.CreateUserRequest;
import com.limoz.fleet.vehicle.FuelType;
import com.limoz.fleet.vehicle.OwnershipType;
import com.limoz.fleet.vehicle.Transmission;
import com.limoz.fleet.vehicle.VehicleCategoryRepository;
import com.limoz.fleet.vehicle.VehicleService;
import com.limoz.fleet.vehicle.dto.VehicleRequest;
import com.limoz.fleet.vehicle.dto.VehicleResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Users for every role, 25 vehicles, 14 drivers, their documents and current assignments. */
@Component
@RequiredArgsConstructor
public class FleetSeedStep implements SeedStep {

    private final UserService userService;
    private final VehicleService vehicleService;
    private final VehicleCategoryRepository categoryRepository;
    private final DriverService driverService;
    private final DocumentService documentService;
    private final DocumentTypeRepository documentTypeRepository;
    private final AssignmentService assignmentService;

    private record V(String plate, String make, String model, int year, String cat, int seats, long odometer, FuelType fuel,
                     OwnershipType ownership, String owner, BigDecimal dayRate) {}

    private record D(String first, String last, String phone, String licence, String city, int licenceMonthsLeft) {}

    @Override
    public int order() {
        return 10;
    }

    @Override
    public String name() {
        return "fleet (users, vehicles, drivers, documents, assignments)";
    }

    @Override
    public void seed(SeedContext ctx) {
        seedUsers(ctx);
        seedVehicles(ctx);
        seedDrivers(ctx);
        seedDocuments(ctx);
        seedAssignments(ctx);
    }

    private void seedUsers(SeedContext ctx) {
        Map<String, String[]> users = Map.ofEntries(
                Map.entry(Roles.IT_ADMIN, new String[]{"Claudine", "Mukamana", "it.admin@limoz.rw"}),
                Map.entry(Roles.MANAGEMENT, new String[]{"Jean Claude", "Niyomugabo", "management@limoz.rw"}),
                Map.entry(Roles.FLEET_MANAGER, new String[]{"Patrick", "Habineza", "fleet.manager@limoz.rw"}),
                Map.entry(Roles.FLEET_OFFICER, new String[]{"Aline", "Uwimana", "fleet.officer@limoz.rw"}),
                Map.entry(Roles.DISPATCHER, new String[]{"Alice", "Uwase", "dispatcher@limoz.rw"}),
                Map.entry(Roles.WORKSHOP_MANAGER, new String[]{"Emmanuel", "Nshuti", "workshop@limoz.rw"}),
                Map.entry(Roles.TECHNICIAN, new String[]{"Theogene", "Bizimana", "technician@limoz.rw"}),
                Map.entry(Roles.FINANCE, new String[]{"Eric", "Mugabo", "finance@limoz.rw"}),
                Map.entry(Roles.COMPLIANCE_OFFICER, new String[]{"Diane", "Ingabire", "compliance@limoz.rw"}),
                Map.entry(Roles.VIEWER, new String[]{"Samuel", "Rwema", "viewer@limoz.rw"}));
        users.forEach((role, u) -> ctx.name("user:" + role, userService.create(new CreateUserRequest(u[0], u[1], u[2], "+25078" + ctx.between(8000000, 8999999),
                null, SeedContext.PASSWORD, Set.of(role), null, false)).id()));
    }

    private void seedVehicles(SeedContext ctx) {
        List<V> vehicles = List.of(
                new V("RAD 408 C", "Toyota", "Hiace", 2021, "MINIBUS", 16, 182600, FuelType.DIESEL, OwnershipType.OWNED, null, m(160000)),
                new V("RAD 112 B", "Toyota", "Coaster", 2020, "COASTER", 29, 96480, FuelType.DIESEL, OwnershipType.OWNED, null, m(260000)),
                new V("RAD 309 A", "Yutong", "ZK6122", 2019, "COACH", 51, 240300, FuelType.DIESEL, OwnershipType.OWNED, null, m(450000)),
                new V("RAD 521 D", "Higer", "KLQ6129", 2022, "COACH", 49, 120800, FuelType.DIESEL, OwnershipType.OWNED, null, m(450000)),
                new V("RAD 633 E", "Toyota", "Hiace", 2022, "MINIBUS", 16, 64120, FuelType.DIESEL, OwnershipType.OWNED, null, m(160000)),
                new V("RAD 744 F", "Hino", "Liesse", 2018, "COASTER", 26, 155010, FuelType.DIESEL, OwnershipType.OWNED, null, m(260000)),
                new V("RAD 855 G", "Hino", "RK", 2017, "COACH", 55, 310200, FuelType.DIESEL, OwnershipType.OWNED, null, m(420000)),
                new V("RAD 966 H", "Mitsubishi", "Fuso Canter", 2020, "CARGO_VAN", 3, 99200, FuelType.DIESEL, OwnershipType.OWNED, null, m(120000)),
                new V("RAD 177 J", "Nissan", "Civilian", 2015, "MINIBUS", 19, 268900, FuelType.DIESEL, OwnershipType.OWNED, null, m(150000)),
                new V("RAD 288 K", "Toyota", "Coaster", 2021, "COASTER", 29, 71400, FuelType.DIESEL, OwnershipType.OWNED, null, m(260000)),
                new V("RAD 399 L", "Yutong", "ZK6120", 2021, "COACH", 53, 88750, FuelType.DIESEL, OwnershipType.OWNED, null, m(450000)),
                new V("RAD 410 M", "Isuzu", "NLR", 2019, "CARGO_VAN", 3, 143300, FuelType.DIESEL, OwnershipType.OWNED, null, m(120000)),
                new V("RAE 101 A", "Toyota", "Land Cruiser V8", 2023, "LUX_SUV", 7, 24500, FuelType.PETROL, OwnershipType.OWNED, null, m(350000)),
                new V("RAE 102 A", "Toyota", "Land Cruiser Prado VX", 2022, "LUX_SUV", 7, 41200, FuelType.DIESEL, OwnershipType.OWNED, null, m(320000)),
                new V("RAE 103 A", "Toyota", "Fortuner", 2021, "SUV", 7, 68900, FuelType.DIESEL, OwnershipType.OWNED, null, m(180000)),
                new V("RAE 104 A", "Mitsubishi", "Pajero Sport", 2020, "SUV", 7, 92100, FuelType.DIESEL, OwnershipType.LEASED, "CFAO Leasing Rwanda", m(170000)),
                new V("RAE 105 A", "Toyota", "Corolla", 2022, "SEDAN", 5, 38600, FuelType.PETROL, OwnershipType.OWNED, null, m(90000)),
                new V("RAE 106 A", "Mercedes-Benz", "E 200", 2021, "LUX_SEDAN", 5, 45300, FuelType.PETROL, OwnershipType.OWNED, null, m(250000)),
                new V("RAE 107 A", "Toyota", "Alphard", 2022, "LUX_VAN", 7, 33800, FuelType.PETROL, OwnershipType.OWNED, null, m(220000)),
                new V("RAE 108 A", "Toyota", "Land Cruiser 79 (pop-up roof)", 2019, "SAFARI", 7, 118400, FuelType.DIESEL, OwnershipType.OWNED, null, m(200000)),
                new V("RAE 109 A", "Toyota", "Land Cruiser 76 (pop-up roof)", 2018, "SAFARI", 7, 131700, FuelType.DIESEL, OwnershipType.OWNED, null, m(200000)),
                new V("RAF 210 B", "Isuzu", "FVR", 2018, "TRUCK", 3, 201500, FuelType.DIESEL, OwnershipType.OWNED, null, m(300000)),
                new V("RAF 211 B", "Mitsubishi", "Fuso FJ", 2020, "FLATBED", 3, 156200, FuelType.DIESEL, OwnershipType.OWNED, null, m(320000)),
                new V("RAG 337 B", "Toyota", "Hiace", 2020, "MINIBUS", 16, 84210, FuelType.DIESEL, OwnershipType.THIRD_PARTY, "420002 GARDEN FRESH Ltd", m(150000)),
                new V("RAG 338 B", "Toyota", "Coaster", 2019, "COASTER", 29, 112450, FuelType.DIESEL, OwnershipType.THIRD_PARTY, "Kigali Coach Owners Coop", m(240000)));
        for (V v : vehicles) {
            Long categoryId = categoryRepository.findByCodeIgnoreCase(v.cat()).orElseThrow().getId();
            VehicleResponse created = vehicleService.create(new VehicleRequest(v.plate(), null, v.make(), v.model(), v.year(), categoryId,
                    v.seats() > 20 ? "Bus" : v.seats() > 7 ? "Van" : v.seats() == 3 ? "Truck" : "4x4", v.fuel(),
                    v.seats() > 20 ? Transmission.MANUAL : Transmission.AUTOMATIC, "ENG-" + v.plate().replace(" ", ""),
                    "JT" + Math.abs(v.plate().hashCode()) + "KL", ctx.pick(List.of("White", "Silver", "Black", "Blue", "Grey")),
                    v.odometer(), v.seats(), LocalDate.of(v.year(), ctx.between(1, 12), ctx.between(1, 28)),
                    m(ctx.between(25, 140) * 1_000_000L), v.ownership(), v.owner(), v.owner() == null ? null : "+250788" + ctx.between(100000, 999999),
                    v.ownership() == OwnershipType.THIRD_PARTY ? "Owner driver " + v.plate().substring(4, 7) : null,
                    ctx.pick(List.of("Radiant Insurance", "Sonarwa", "Prime Insurance", "SANLAM")), "POL-" + ctx.between(100000, 999999),
                    ctx.today().plusMonths(ctx.between(-1, 11)), v.dayRate(), ctx.pick(List.of("Operations", "Operations", "Executive", "Logistics")), null));
            ctx.add("vehicle", created.id());
            ctx.name("vehicle:" + v.plate(), created.id());
        }
    }

    private void seedDrivers(SeedContext ctx) {
        List<D> drivers = List.of(
                new D("Jean", "Uwimana", "+250788441200", "RW-DL-44120", "Kigali", 30),
                new D("Alice", "Mukamana", "+250788398870", "RW-DL-39887", "Huye", 18),
                new D("Patrick", "Habimana", "+250788502310", "RW-DL-50231", "Rubavu", 42),
                new D("Eric", "Niyonzima", "+250788419020", "RW-DL-41902", "Musanze", 6),
                new D("Diane", "Ingabire", "+250788477130", "RW-DL-47713", "Kigali", 24),
                new D("Samuel", "Rwema", "+250788520040", "RW-DL-52004", "Huye", 1),
                new D("Grace", "Umutoni", "+250788488650", "RW-DL-48865", "Kigali", 36),
                new D("Olivier", "Mugisha", "+250788433980", "RW-DL-43398", "Muhanga", -2),
                new D("Joseph", "Habimana", "+250788123456", "RW-DL-55010", "Kigali", 20),
                new D("Claude", "Nsengiyumva", "+250788612233", "RW-DL-55011", "Kigali", 15),
                new D("Innocent", "Kayitare", "+250788771122", "RW-DL-55012", "Rubavu", 28),
                new D("Fabrice", "Tuyishime", "+250788990011", "RW-DL-55013", "Kigali", 33),
                new D("Chantal", "Mukeshimana", "+250788334455", "RW-DL-55014", "Musanze", 9),
                new D("Yves", "Ndayishimiye", "+250788556677", "RW-DL-55015", "Kigali", 12));
        int i = 0;
        for (D d : drivers) {
            DriverResponse created = driverService.create(new DriverRequest(d.first(), d.last(), d.phone(),
                    (d.first() + "." + d.last()).toLowerCase() + "@limoz.rw", "1 1990 8 00" + String.format("%05d", 10000 + i), d.licence(), "D",
                    ctx.today().minusYears(5).plusMonths(d.licenceMonthsLeft()), ctx.today().plusMonths(d.licenceMonthsLeft()),
                    i < 11 ? EmploymentStatus.FULL_TIME : EmploymentStatus.CONTRACT, d.city(), "Next of kin " + d.last(), "+250788000" + String.format("%03d", i),
                    ctx.today().minusYears(ctx.between(1, 8)).minusDays(ctx.between(0, 300)), LocalDate.of(ctx.between(1975, 1998), ctx.between(1, 12), ctx.between(1, 28)), null, null));
            ctx.add("driver", created.id());
            ctx.name("driver:" + d.licence(), created.id());
            i++;
        }
        // one driver has a login account (DRIVER role) to exercise the "my trips" endpoints
        userService.create(new CreateUserRequest("Jean", "Uwimana", "driver@limoz.rw", "+250788441200", null, SeedContext.PASSWORD,
                Set.of(Roles.DRIVER), ctx.id("driver:RW-DL-44120"), false));
    }

    private void seedDocuments(SeedContext ctx) {
        Long insurance = documentTypeRepository.findByCodeIgnoreCase("INSURANCE").orElseThrow().getId();
        Long inspection = documentTypeRepository.findByCodeIgnoreCase("INSPECTION").orElseThrow().getId();
        Long roadLicence = documentTypeRepository.findByCodeIgnoreCase("ROAD_LICENCE").orElseThrow().getId();
        Long rura = documentTypeRepository.findByCodeIgnoreCase("RURA_PERMIT").orElseThrow().getId();
        Long drivingLicence = documentTypeRepository.findByCodeIgnoreCase("DRIVING_LICENCE").orElseThrow().getId();
        List<Long> vehicles = ctx.ids("vehicle");
        for (int i = 0; i < vehicles.size(); i++) {
            Long vehicleId = vehicles.get(i);
            // a few deliberately expired / expiring documents feed the alert centre and reports (RAD 309 A insurance expired, RAD 112 B RURA expiring...)
            int insuranceOffset = i == 2 ? -3 : i == 8 ? -40 : i == 5 ? 12 : ctx.between(40, 330);
            int ruraOffset = i == 1 ? 10 : i == 5 ? 20 : ctx.between(45, 300);
            int inspectionOffset = i == 5 ? 14 : i == 8 ? -10 : ctx.between(30, 340);
            documentService.addVehicleDocument(vehicleId, doc(insurance, "INS-" + (20260 + i), ctx.pick(List.of("Radiant Insurance", "Sonarwa", "Prime Insurance")),
                    ctx.today().plusDays(insuranceOffset).minusYears(1), ctx.today().plusDays(insuranceOffset), m(ctx.between(600, 1800) * 1000L)));
            documentService.addVehicleDocument(vehicleId, doc(inspection, "SGS-" + (118000 + i), "SGS Rwanda",
                    ctx.today().plusDays(inspectionOffset).minusYears(1), ctx.today().plusDays(inspectionOffset), m(35000)));
            documentService.addVehicleDocument(vehicleId, doc(roadLicence, "RL-" + (7000 + i), "RRA",
                    ctx.today().plusDays(ctx.between(20, 300)).minusYears(1), ctx.today().plusDays(ctx.between(20, 300)), m(120000)));
            documentService.addVehicleDocument(vehicleId, doc(rura, "RURA-" + (5100 + i), "RURA",
                    ctx.today().plusDays(ruraOffset).minusYears(1), ctx.today().plusDays(ruraOffset), m(150000)));
        }
        for (Long driverId : ctx.ids("driver")) {
            DriverResponse d = driverService.get(driverId);
            documentService.addDriverDocument(driverId, doc(drivingLicence, d.licenseNumber(), "Rwanda National Police", d.licenseIssueDate(), d.licenseExpiryDate(), null));
        }
    }

    private void seedAssignments(SeedContext ctx) {
        String[][] pairs = {{"RAD 408 C", "RW-DL-44120"}, {"RAD 112 B", "RW-DL-39887"}, {"RAD 521 D", "RW-DL-50231"}, {"RAD 633 E", "RW-DL-47713"},
                {"RAD 744 F", "RW-DL-55010"}, {"RAD 855 G", "RW-DL-48865"}, {"RAE 101 A", "RW-DL-55011"}, {"RAE 102 A", "RW-DL-55012"},
                {"RAE 107 A", "RW-DL-55013"}, {"RAF 210 B", "RW-DL-55015"}};
        for (String[] p : pairs) {
            assignmentService.assign(new AssignmentRequest(ctx.id("vehicle:" + p[0]), ctx.id("driver:" + p[1]),
                    ctx.at(ctx.today().minusDays(ctx.between(10, 120)), 8, 0), "Regular driver", null, null));
        }
    }

    private static DocumentRequest doc(Long typeId, String number, String issuer, LocalDate issued, LocalDate expiry, BigDecimal cost) {
        return new DocumentRequest(typeId, number, issuer, issued, expiry, null, cost, null);
    }

    private static BigDecimal m(long value) {
        return BigDecimal.valueOf(value);
    }
}
