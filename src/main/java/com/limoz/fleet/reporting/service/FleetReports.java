package com.limoz.fleet.reporting.service;

import com.limoz.fleet.reporting.domain.ReportParams;

import com.limoz.fleet.document.domain.DocumentStatus;
import com.limoz.fleet.document.domain.VehicleDocument;
import com.limoz.fleet.document.repository.VehicleDocumentRepository;
import com.limoz.fleet.driver.domain.Driver;
import com.limoz.fleet.driver.repository.DriverRepository;
import com.limoz.fleet.reporting.export.domain.ReportTable;
import com.limoz.fleet.vehicle.domain.Vehicle;
import com.limoz.fleet.vehicle.repository.VehicleRepository;
import com.limoz.fleet.vehicle.domain.VehicleStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Reports that only need fleet master data: daily fleet position, expired documents, fleet availability, driver roster. */
@Configuration
@RequiredArgsConstructor
public class FleetReports {

    private final VehicleRepository vehicleRepository;
    private final DriverRepository driverRepository;
    private final VehicleDocumentRepository vehicleDocumentRepository;
    private final Clock clock;

    @Bean
    ReportProvider dailyFleetReport() {
        return new ReportProvider() {
            public String code() { return "daily-fleet"; }
            public String title() { return "Daily Fleet Report"; }
            public String description() { return "Operational position of every vehicle: category, driver, status and remarks."; }
            public ReportTable build(ReportParams p) {
                LocalDate date = p.dateOr(LocalDate.now(clock));
                Map<Long, List<VehicleDocument>> problems = vehicleDocumentRepository
                        .findActiveByStatusIn(List.of(DocumentStatus.EXPIRED, DocumentStatus.EXPIRING_SOON)).stream()
                        .collect(Collectors.groupingBy(d -> d.getVehicle().getId()));
                ReportTable.Builder b = ReportTable.builder(code(), title()).meta("Date", date)
                        .text("plate", "Plate").text("vehicle", "Vehicle").text("category", "Category").text("driver", "Driver")
                        .text("status", "Operational status").text("maintenance", "Maintenance").number("odometer", "Odometer (km)")
                        .text("remarks", "Remarks");
                for (Vehicle v : vehicleRepository.findByArchivedFalseOrderByPlateNumberAsc()) {
                    if (p.categoryId() != null && !v.getCategory().getId().equals(p.categoryId())) continue;
                    if (p.department() != null && !p.department().equalsIgnoreCase(v.getDepartment())) continue;
                    List<String> remarks = new ArrayList<>();
                    for (VehicleDocument d : problems.getOrDefault(v.getId(), List.of())) {
                        remarks.add(d.getDocumentType().getName() + " " + d.getStatus().name().toLowerCase().replace('_', ' ')
                                + (d.getExpiryDate() == null ? "" : " (" + d.getExpiryDate() + ")"));
                    }
                    b.row(v.getPlateNumber(), v.getMake() + " " + v.getModel(), v.getCategory().getName(),
                            v.getCurrentDriver() == null ? "-" : v.getCurrentDriver().getFullName(),
                            v.getOperationalStatus(), v.getMaintenanceStatus(), v.getOdometerKm(), String.join("; ", remarks));
                }
                return b.build();
            }
        };
    }

    @Bean
    ReportProvider expiredDocumentsReport() {
        return new ReportProvider() {
            public String code() { return "expired-documents"; }
            public String title() { return "Expired & Expiring Documents"; }
            public String description() { return "Vehicle certificates that are expired or expiring soon, oldest first."; }
            public ReportTable build(ReportParams p) {
                List<DocumentStatus> statuses = p.status() == null
                        ? List.of(DocumentStatus.EXPIRED, DocumentStatus.EXPIRING_SOON)
                        : List.of(DocumentStatus.valueOf(p.status().toUpperCase()));
                ReportTable.Builder b = ReportTable.builder(code(), title()).meta("Generated", LocalDate.now(clock))
                        .text("plate", "Plate").text("vehicle", "Vehicle").text("document", "Document").text("number", "Number")
                        .text("issuer", "Issuer").text("issued", "Issued").text("expiry", "Expiry").text("status", "Status")
                        .text("required", "Required for dispatch");
                for (VehicleDocument d : vehicleDocumentRepository.findActiveByStatusIn(statuses)) {
                    Vehicle v = d.getVehicle();
                    if (p.vehicleId() != null && !v.getId().equals(p.vehicleId())) continue;
                    if (p.categoryId() != null && !v.getCategory().getId().equals(p.categoryId())) continue;
                    b.row(v.getPlateNumber(), v.getMake() + " " + v.getModel(), d.getDocumentType().getName(), d.getDocumentNumber(),
                            d.getIssuer(), d.getIssueDate(), d.getExpiryDate(), d.getStatus(), d.getDocumentType().isRequiredForDispatch() ? "Yes" : "No");
                }
                return b.build();
            }
        };
    }

    @Bean
    ReportProvider fleetAvailabilityReport() {
        return new ReportProvider() {
            public String code() { return "fleet-availability"; }
            public String title() { return "Fleet Availability"; }
            public String description() { return "Vehicle counts per operational status and category."; }
            public ReportTable build(ReportParams p) {
                List<Vehicle> vehicles = vehicleRepository.findByArchivedFalseOrderByPlateNumberAsc();
                Map<String, Map<VehicleStatus, Long>> byCategory = vehicles.stream().collect(Collectors.groupingBy(
                        v -> v.getCategory().getName(), java.util.TreeMap::new,
                        Collectors.groupingBy(Vehicle::getOperationalStatus, Collectors.counting())));
                ReportTable.Builder b = ReportTable.builder(code(), title()).meta("Date", LocalDate.now(clock)).text("category", "Category");
                for (VehicleStatus s : VehicleStatus.values()) b.number(s.name().toLowerCase(), s.name().replace('_', ' '));
                b.number("total", "Total").number("availability", "Availability %");
                long[] totals = new long[VehicleStatus.values().length + 1];
                for (var entry : byCategory.entrySet()) {
                    List<Object> row = new ArrayList<>();
                    row.add(entry.getKey());
                    long total = 0;
                    int i = 0;
                    for (VehicleStatus s : VehicleStatus.values()) {
                        long n = entry.getValue().getOrDefault(s, 0L);
                        row.add(n);
                        totals[i++] += n;
                        total += n;
                    }
                    totals[i] += total;
                    long available = entry.getValue().getOrDefault(VehicleStatus.AVAILABLE, 0L)
                            + entry.getValue().getOrDefault(VehicleStatus.ASSIGNED, 0L)
                            + entry.getValue().getOrDefault(VehicleStatus.ON_TRIP, 0L)
                            + entry.getValue().getOrDefault(VehicleStatus.RESERVED, 0L);
                    row.add(total);
                    row.add(total == 0 ? 0 : Math.round(available * 100.0 / total));
                    b.rowNullable(row);
                }
                List<Object> totalRow = new ArrayList<>();
                totalRow.add("Total");
                for (int i = 0; i < VehicleStatus.values().length; i++) totalRow.add(totals[i]);
                totalRow.add(totals[VehicleStatus.values().length]);
                long operational = vehicles.stream().filter(v -> v.getOperationalStatus().isDispatchable() || v.getOperationalStatus() == VehicleStatus.ON_TRIP).count();
                totalRow.add(vehicles.isEmpty() ? 0 : Math.round(operational * 100.0 / vehicles.size()));
                b.totals(totalRow.toArray());
                return b.build();
            }
        };
    }

    @Bean
    ReportProvider driverRosterReport() {
        return new ReportProvider() {
            public String code() { return "driver-roster"; }
            public String title() { return "Driver Roster & Licences"; }
            public String description() { return "All active drivers with status, current vehicle and licence validity."; }
            public ReportTable build(ReportParams p) {
                LocalDate today = LocalDate.now(clock);
                ReportTable.Builder b = ReportTable.builder(code(), title()).meta("Date", today)
                        .text("code", "Driver code").text("name", "Driver").text("phone", "Phone").text("status", "Status")
                        .text("vehicle", "Current vehicle").text("licence", "Licence").text("expiry", "Licence expiry").text("licenceStatus", "Licence status");
                for (Driver d : driverRepository.findByArchivedFalseOrderByLastNameAscFirstNameAsc()) {
                    if (p.status() != null && !d.getStatus().name().equalsIgnoreCase(p.status())) continue;
                    String licenceStatus = d.getLicenseExpiryDate() == null ? "UNKNOWN"
                            : d.getLicenseExpiryDate().isBefore(today) ? "EXPIRED"
                            : !d.getLicenseExpiryDate().isAfter(today.plusDays(30)) ? "EXPIRING SOON" : "VALID";
                    b.row(d.getDriverCode(), d.getFullName(), d.getPhone(), d.getStatus(),
                            d.getCurrentVehicle() == null ? "-" : d.getCurrentVehicle().getPlateNumber(),
                            d.getLicenseNumber(), d.getLicenseExpiryDate(), licenceStatus);
                }
                return b.build();
            }
        };
    }
}
