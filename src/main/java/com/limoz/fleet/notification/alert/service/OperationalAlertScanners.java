package com.limoz.fleet.notification.alert.service;

import com.limoz.fleet.notification.alert.domain.AlertCandidate;
import com.limoz.fleet.notification.alert.domain.AlertType;
import com.limoz.fleet.notification.domain.NotificationSeverity;
import com.limoz.fleet.settings.domain.SettingKeys;
import com.limoz.fleet.settings.service.SettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * Alert scanners for the workshop, fuel, compliance, dispatch and finance modules. They query the operational
 * tables directly (read-only aggregate SQL) so that the alert centre stays decoupled from module internals.
 */
@Configuration
@RequiredArgsConstructor
public class OperationalAlertScanners {

    private final JdbcClient jdbc;
    private final SettingsService settings;
    private final Clock clock;
    private final ZoneId zone;

    @Bean
    AlertScanner maintenanceDueScanner() {
        return () -> jdbc.sql("""
                SELECT s.id, s.status, v.id AS vehicle_id, v.plate_number, t.name, s.next_service_odometer, s.next_service_date, v.odometer_km
                FROM maintenance_schedules s JOIN vehicles v ON v.id = s.vehicle_id JOIN service_types t ON t.id = s.service_type_id
                WHERE s.active AND v.archived = FALSE AND s.status IN ('DUE_SOON','OVERDUE')""")
                .query((rs, i) -> {
                    boolean overdue = "OVERDUE".equals(rs.getString(2));
                    AlertType type = overdue ? AlertType.MAINTENANCE_OVERDUE : AlertType.MAINTENANCE_DUE;
                    String plate = rs.getString(4);
                    String detail = (rs.getObject(6) == null ? "" : "next at " + rs.getLong(6) + " km (now " + rs.getLong(8) + " km)")
                            + (rs.getDate(7) == null ? "" : (rs.getObject(6) == null ? "" : ", ") + "due " + rs.getDate(7).toLocalDate());
                    return new AlertCandidate(type, overdue ? NotificationSeverity.CRITICAL : NotificationSeverity.WARNING,
                            (overdue ? "Service overdue" : "Service approaching") + " · " + plate,
                            rs.getString(5) + " for " + plate + ": " + detail, "MaintenanceSchedule", rs.getLong(1), plate,
                            "/vehicles/" + rs.getLong(3) + "?tab=maintenance", AlertCandidate.key(type, "MaintenanceSchedule", rs.getLong(1), null));
                }).list();
    }

    @Bean
    AlertScanner workshopStayScanner() {
        return () -> {
            int redDays = settings.getIntOrDefault("maintenance.garage_red_days", 6);
            return jdbc.sql("""
                    SELECT m.id, m.maintenance_number, v.id, v.plate_number, m.status,
                           EXTRACT(DAY FROM (NOW() - m.reported_at))::int AS days
                    FROM maintenance_records m JOIN vehicles v ON v.id = m.vehicle_id
                    WHERE m.status IN ('IN_PROGRESS','WAITING_FOR_PARTS','APPROVED','INSPECTION','REPORTED')
                      AND m.reported_at < NOW() - make_interval(days => :days)""")
                    .param("days", redDays)
                    .query((rs, i) -> new AlertCandidate(AlertType.VEHICLE_IN_WORKSHOP_LONG, NotificationSeverity.WARNING,
                            "Long workshop stay · " + rs.getString(4),
                            rs.getString(2) + " has been open for " + rs.getInt(6) + " days (" + rs.getString(5).replace('_', ' ') + ")",
                            "MaintenanceRecord", rs.getLong(1), rs.getString(2), "/maintenance/" + rs.getLong(1),
                            AlertCandidate.key(AlertType.VEHICLE_IN_WORKSHOP_LONG, "MaintenanceRecord", rs.getLong(1), null)))
                    .list();
        };
    }

    @Bean
    AlertScanner fuelAnomalyScanner() {
        return () -> jdbc.sql("""
                SELECT f.id, v.id, v.plate_number, f.anomaly_reason, f.litres, f.consumption_l_per_100km, f.transaction_at
                FROM fuel_transactions f JOIN vehicles v ON v.id = f.vehicle_id
                WHERE f.anomaly = TRUE AND f.archived = FALSE AND f.transaction_at > NOW() - interval '14 days'""")
                .query((rs, i) -> new AlertCandidate(AlertType.FUEL_ANOMALY, NotificationSeverity.WARNING,
                        "Fuel anomaly · " + rs.getString(3),
                        (rs.getString(4) == null ? "Abnormal refuelling" : rs.getString(4)) + " (" + rs.getBigDecimal(5).stripTrailingZeros().toPlainString() + " L)",
                        "FuelTransaction", rs.getLong(1), rs.getString(3), "/vehicles/" + rs.getLong(2) + "?tab=fuel",
                        AlertCandidate.key(AlertType.FUEL_ANOMALY, "FuelTransaction", rs.getLong(1), null)))
                .list();
    }

    @Bean
    AlertScanner unpaidFineScanner() {
        return () -> jdbc.sql("""
                SELECT f.id, f.fine_number, v.id, v.plate_number, f.offence, f.amount, f.due_date
                FROM traffic_fines f JOIN vehicles v ON v.id = f.vehicle_id WHERE f.status = 'UNPAID'""")
                .query((rs, i) -> {
                    boolean overdue = rs.getDate(7) != null && rs.getDate(7).toLocalDate().isBefore(LocalDate.now(clock));
                    return new AlertCandidate(AlertType.FINE_UNPAID, overdue ? NotificationSeverity.CRITICAL : NotificationSeverity.WARNING,
                            "Traffic fine unpaid · " + rs.getString(4),
                            rs.getString(2) + ": " + rs.getString(5) + " · " + rs.getBigDecimal(6).stripTrailingZeros().toPlainString() + " RWF"
                                    + (overdue ? " (past due " + rs.getDate(7).toLocalDate() + ")" : ""),
                            "TrafficFine", rs.getLong(1), rs.getString(2), "/fines/" + rs.getLong(1),
                            AlertCandidate.key(AlertType.FINE_UNPAID, "TrafficFine", rs.getLong(1), null));
                }).list();
    }

    @Bean
    AlertScanner overdueInvoiceScanner() {
        return () -> jdbc.sql("""
                SELECT i.id, i.invoice_number, c.name, i.total_amount - i.amount_paid, i.due_date, i.currency
                FROM invoices i JOIN customers c ON c.id = i.customer_id
                WHERE i.status = 'OVERDUE' OR (i.status IN ('ISSUED','PARTIALLY_PAID') AND i.due_date < :today::date)""")
                .param("today", LocalDate.now(clock))
                .query((rs, i) -> new AlertCandidate(AlertType.INVOICE_OVERDUE, NotificationSeverity.WARNING,
                        "Invoice overdue · " + rs.getString(3),
                        rs.getString(2) + " outstanding " + rs.getBigDecimal(4).stripTrailingZeros().toPlainString() + " " + rs.getString(6) + ", due " + rs.getDate(5).toLocalDate(),
                        "Invoice", rs.getLong(1), rs.getString(2), "/invoices/" + rs.getLong(1),
                        AlertCandidate.key(AlertType.INVOICE_OVERDUE, "Invoice", rs.getLong(1), null)))
                .list();
    }

    @Bean
    AlertScanner bookingScanner() {
        return () -> {
            int hours = settings.getInt(SettingKeys.BOOKING_REMINDER_HOURS);
            LocalDate today = LocalDate.now(clock);
            LocalDate horizon = today.plusDays(Math.max(1, hours / 24 + 1));
            return jdbc.sql("""
                    SELECT b.id, b.booking_number, c.name, b.start_date, b.status,
                           COUNT(s.id) FILTER (WHERE s.status = 'UNASSIGNED') AS unassigned, COUNT(s.id) AS slots
                    FROM bookings b JOIN customers c ON c.id = b.customer_id LEFT JOIN booking_slots s ON s.booking_id = b.id
                    WHERE b.status IN ('CONFIRMED','READY_FOR_DEPLOYMENT','REQUESTED') AND b.start_date BETWEEN :today::date AND :horizon::date
                    GROUP BY b.id, b.booking_number, c.name, b.start_date, b.status""")
                    .param("today", today).param("horizon", horizon)
                    .query((rs, i) -> {
                        long unassigned = rs.getLong(6);
                        boolean gap = unassigned > 0;
                        AlertType type = gap ? AlertType.BOOKING_UNASSIGNED : AlertType.BOOKING_APPROACHING;
                        return new AlertCandidate(type, gap ? NotificationSeverity.WARNING : NotificationSeverity.INFO,
                                (gap ? "Booking not fully assigned · " : "Booking approaching · ") + rs.getString(3),
                                rs.getString(2) + " starts " + rs.getDate(4).toLocalDate() + (gap ? " with " + unassigned + " of " + rs.getLong(7) + " vehicles unassigned" : ""),
                                "Booking", rs.getLong(1), rs.getString(2), "/bookings/" + rs.getLong(1),
                                AlertCandidate.key(type, "Booking", rs.getLong(1), null));
                    }).list();
        };
    }

    @Bean
    AlertScanner lowStockScanner() {
        return () -> jdbc.sql("""
                SELECT id, part_number, name, current_stock, minimum_stock FROM spare_parts
                WHERE active AND current_stock <= minimum_stock""")
                .query((rs, i) -> new AlertCandidate(AlertType.LOW_STOCK, rs.getInt(4) == 0 ? NotificationSeverity.WARNING : NotificationSeverity.INFO,
                        (rs.getInt(4) == 0 ? "Out of stock · " : "Low stock · ") + rs.getString(3),
                        rs.getString(2) + " " + rs.getString(3) + ": " + rs.getInt(4) + " in stock (minimum " + rs.getInt(5) + ")",
                        "SparePart", rs.getLong(1), rs.getString(2), "/spare-parts/" + rs.getLong(1),
                        AlertCandidate.key(AlertType.LOW_STOCK, "SparePart", rs.getLong(1), null)))
                .list();
    }

    @Bean
    AlertScanner contractExpiryScanner() {
        return () -> {
            List<AlertCandidate> out = new java.util.ArrayList<>();
            out.addAll(jdbc.sql("""
                    SELECT c.id, c.reference, cu.name, c.period_end FROM commitments c JOIN customers cu ON cu.id = c.customer_id
                    WHERE c.status = 'EXPIRING_SOON'""")
                    .query((rs, i) -> new AlertCandidate(AlertType.COMMITMENT_EXPIRING, NotificationSeverity.INFO,
                            "Commitment expiring · " + rs.getString(3), rs.getString(2) + " ends " + rs.getDate(4).toLocalDate(),
                            "Commitment", rs.getLong(1), rs.getString(2), "/commitments/" + rs.getLong(1),
                            AlertCandidate.key(AlertType.COMMITMENT_EXPIRING, "Commitment", rs.getLong(1), null))).list());
            out.addAll(jdbc.sql("""
                    SELECT p.id, p.lpo_number, cu.name, p.expiry_date FROM purchase_orders p JOIN customers cu ON cu.id = p.customer_id
                    WHERE p.status IN ('OPEN','PART_INVOICED') AND p.expiry_date IS NOT NULL AND p.expiry_date <= :horizon::date""")
                    .param("horizon", LocalDate.now(clock).plusDays(settings.getInt(SettingKeys.DOCUMENT_EXPIRY_WARNING_DAYS)))
                    .query((rs, i) -> new AlertCandidate(AlertType.LPO_EXPIRING, NotificationSeverity.WARNING,
                            "LPO expiring · " + rs.getString(3), rs.getString(2) + " expires " + rs.getDate(4).toLocalDate() + " and is not fully invoiced",
                            "PurchaseOrder", rs.getLong(1), rs.getString(2), "/purchase-orders/" + rs.getLong(1),
                            AlertCandidate.key(AlertType.LPO_EXPIRING, "PurchaseOrder", rs.getLong(1), null))).list());
            return out;
        };
    }

    @Bean
    AlertScanner openIncidentScanner() {
        return () -> jdbc.sql("""
                SELECT i.id, i.incident_number, v.plate_number, i.incident_type, i.severity, i.status, i.occurred_at
                FROM incidents i JOIN vehicles v ON v.id = i.vehicle_id WHERE i.status IN ('OPEN','UNDER_INVESTIGATION')""")
                .query((rs, i) -> {
                    boolean accident = "ACCIDENT".equals(rs.getString(4));
                    boolean severe = "MAJOR".equals(rs.getString(5)) || "CRITICAL".equals(rs.getString(5));
                    AlertType type = accident ? AlertType.ACCIDENT_REPORTED : AlertType.INCIDENT_OPEN;
                    return new AlertCandidate(type, severe ? NotificationSeverity.CRITICAL : NotificationSeverity.WARNING,
                            (accident ? "Accident · " : "Incident open · ") + rs.getString(3),
                            rs.getString(2) + " (" + rs.getString(5) + " " + rs.getString(4).toLowerCase() + ") is " + rs.getString(6).toLowerCase().replace('_', ' '),
                            "Incident", rs.getLong(1), rs.getString(2), "/incidents/" + rs.getLong(1),
                            AlertCandidate.key(type, "Incident", rs.getLong(1), null));
                }).list();
    }
}
