package com.limoz.fleet.reporting.service;

import com.limoz.fleet.reporting.domain.ReportParams;
import com.limoz.fleet.reporting.export.domain.ReportTable;
import com.limoz.fleet.security.Permissions;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.core.simple.JdbcClient.StatementSpec;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;

/**
 * Maintenance, fuel, cost, incident and compliance reports built with aggregate SQL over the operational tables.
 * Includes the three reference-application reports (Technical, Fuel GSL, Deployment is in OperationsReports).
 */
@Configuration
@RequiredArgsConstructor
public class WorkshopFinanceReports {

    private final JdbcClient jdbc;
    private final Clock clock;
    private final ZoneId zone;

    private LocalDate from(ReportParams p, int defaultDays) {
        return p.fromOr(to(p).minusDays(defaultDays));
    }

    private LocalDate to(ReportParams p) {
        return p.toOr(LocalDate.now(clock));
    }

    private StatementSpec bind(String sql, ReportParams p, LocalDate from, LocalDate to) {
        return jdbc.sql(sql)
                .param("from", from).param("to", to)
                .param("start", Timestamp.from(from.atStartOfDay(zone).toInstant()))
                .param("end", Timestamp.from(to.plusDays(1).atStartOfDay(zone).toInstant()))
                .param("vehicleId", p.vehicleId()).param("driverId", p.driverId()).param("categoryId", p.categoryId())
                .param("customerId", p.customerId()).param("status", p.status());
    }

    private static Object ts(ResultSet rs, int i) throws SQLException {
        Timestamp t = rs.getTimestamp(i);
        return t == null ? null : t.toInstant();
    }

    private static Object date(ResultSet rs, int i) throws SQLException {
        java.sql.Date d = rs.getDate(i);
        return d == null ? null : d.toLocalDate();
    }

    private abstract static class Base implements ReportProvider {
        final String code, title, description, permission;
        Base(String code, String title, String description, String permission) {
            this.code = code; this.title = title; this.description = description; this.permission = permission;
        }
        public String code() { return code; }
        public String title() { return title; }
        public String description() { return description; }
        public String additionalPermission() { return permission; }
    }

    @Bean
    ReportProvider maintenanceReport() {
        return new Base("maintenance", "Maintenance Report", "Maintenance jobs in the period with workshop, status, days in garage and cost.", null) {
            public ReportTable build(ReportParams p) {
                LocalDate from = from(p, 29), to = to(p);
                ReportTable.Builder b = ReportTable.builder(code, title).meta("From", from).meta("To", to)
                        .text("number", "Job").text("intake", "Intake").text("plate", "Plate").text("type", "Type").text("priority", "Priority")
                        .text("complaint", "Complaint").text("workshop", "Workshop").text("reported", "Reported").text("completed", "Completed")
                        .number("days", "Days").number("labour", "Labour").number("parts", "Parts").number("total", "Total").text("payment", "Payment").text("status", "Status");
                BigDecimal[] totals = {BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO};
                bind("""
                        SELECT m.maintenance_number, m.intake_number, v.plate_number, m.maintenance_type, m.priority, m.complaint, w.name,
                               m.reported_at, m.completed_at, EXTRACT(DAY FROM (COALESCE(m.released_at, m.completed_at, NOW()) - m.reported_at))::int,
                               m.labor_cost, m.parts_cost, m.total_cost, m.payment_status, m.status
                        FROM maintenance_records m JOIN vehicles v ON v.id = m.vehicle_id LEFT JOIN workshops w ON w.id = m.workshop_id
                        WHERE m.reported_at >= :start AND m.reported_at < :end
                          AND (:vehicleId::bigint IS NULL OR m.vehicle_id = :vehicleId::bigint)
                          AND (:categoryId::bigint IS NULL OR v.category_id = :categoryId::bigint)
                          AND (:status::varchar IS NULL OR m.status = :status::varchar)
                        ORDER BY m.reported_at DESC""", p, from, to)
                        .query((rs, i) -> {
                            totals[0] = totals[0].add(rs.getBigDecimal(11)); totals[1] = totals[1].add(rs.getBigDecimal(12)); totals[2] = totals[2].add(rs.getBigDecimal(13));
                            b.row(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7),
                                    ts(rs, 8), ts(rs, 9), rs.getInt(10), rs.getBigDecimal(11), rs.getBigDecimal(12), rs.getBigDecimal(13), rs.getString(14), rs.getString(15));
                            return 1;
                        }).list();
                b.totals("Total", "", "", "", "", "", "", "", "", "", totals[0], totals[1], totals[2], "", "");
                return b.build();
            }
        };
    }

    @Bean
    ReportProvider maintenanceCostReport() {
        return new Base("maintenance-cost", "Maintenance Cost by Vehicle", "Jobs, labour, parts and total maintenance cost per vehicle, plus cost per km.", null) {
            public ReportTable build(ReportParams p) {
                LocalDate from = from(p, 89), to = to(p);
                ReportTable.Builder b = ReportTable.builder(code, title).meta("From", from).meta("To", to)
                        .text("plate", "Plate").text("vehicle", "Vehicle").text("category", "Category").number("jobs", "Jobs")
                        .number("labour", "Labour").number("parts", "Parts").number("other", "Other").number("total", "Total").number("km", "Distance (km)").number("perKm", "Cost / km");
                bind("""
                        SELECT v.plate_number, v.make || ' ' || v.model, c.name, COUNT(m.id), COALESCE(SUM(m.labor_cost),0), COALESCE(SUM(m.parts_cost),0),
                               COALESCE(SUM(m.other_cost),0), COALESCE(SUM(m.total_cost),0),
                               (SELECT COALESCE(SUM(t.distance_km),0) FROM trips t WHERE t.vehicle_id = v.id AND t.status = 'COMPLETED' AND t.ended_at >= :start AND t.ended_at < :end)
                        FROM vehicles v JOIN vehicle_categories c ON c.id = v.category_id
                        LEFT JOIN maintenance_records m ON m.vehicle_id = v.id AND m.status <> 'CANCELLED' AND m.reported_at >= :start AND m.reported_at < :end
                        WHERE v.archived = FALSE AND (:categoryId::bigint IS NULL OR v.category_id = :categoryId::bigint) AND (:vehicleId::bigint IS NULL OR v.id = :vehicleId::bigint)
                        GROUP BY v.id, v.plate_number, v.make, v.model, c.name ORDER BY 8 DESC""", p, from, to)
                        .query((rs, i) -> {
                            BigDecimal total = rs.getBigDecimal(8), km = rs.getBigDecimal(9);
                            b.row(rs.getString(1), rs.getString(2), rs.getString(3), rs.getLong(4), rs.getBigDecimal(5), rs.getBigDecimal(6), rs.getBigDecimal(7), total, km,
                                    km.signum() == 0 ? null : total.divide(km, 2, java.math.RoundingMode.HALF_UP));
                            return 1;
                        }).list();
                return b.build();
            }
        };
    }

    @Bean
    ReportProvider fuelConsumptionReport() {
        return new Base("fuel-consumption", "Fuel Consumption", "Litres, cost, distance and L/100 km per vehicle in the period.", Permissions.FUEL_READ) {
            public ReportTable build(ReportParams p) {
                LocalDate from = from(p, 29), to = to(p);
                ReportTable.Builder b = ReportTable.builder(code, title).meta("From", from).meta("To", to)
                        .text("plate", "Plate").text("category", "Category").number("fills", "Fills").number("litres", "Litres").number("cost", "Cost (RWF)")
                        .number("km", "Distance (km)").number("l100", "L/100 km").number("kmpl", "km/L").number("anomalies", "Anomalies");
                BigDecimal[] t = {BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO};
                bind("""
                        SELECT v.plate_number, c.name, COUNT(f.id), COALESCE(SUM(f.litres),0), COALESCE(SUM(f.total_amount),0),
                               COALESCE(SUM(f.distance_since_last_km),0), COALESCE(SUM(f.litres) FILTER (WHERE f.distance_since_last_km > 0),0), COUNT(f.id) FILTER (WHERE f.anomaly)
                        FROM fuel_transactions f JOIN vehicles v ON v.id = f.vehicle_id JOIN vehicle_categories c ON c.id = v.category_id
                        WHERE f.archived = FALSE AND f.transaction_at >= :start AND f.transaction_at < :end
                          AND (:vehicleId::bigint IS NULL OR f.vehicle_id = :vehicleId::bigint) AND (:categoryId::bigint IS NULL OR v.category_id = :categoryId::bigint)
                          AND (:driverId::bigint IS NULL OR f.driver_id = :driverId::bigint)
                        GROUP BY v.plate_number, c.name ORDER BY 4 DESC""", p, from, to)
                        .query((rs, i) -> {
                            BigDecimal litres = rs.getBigDecimal(4), cost = rs.getBigDecimal(5), km = rs.getBigDecimal(6), litresWithKm = rs.getBigDecimal(7);
                            t[0] = t[0].add(litres); t[1] = t[1].add(cost); t[2] = t[2].add(km);
                            b.row(rs.getString(1), rs.getString(2), rs.getLong(3), litres, cost, km,
                                    km.signum() == 0 ? null : litresWithKm.multiply(BigDecimal.valueOf(100)).divide(km, 2, java.math.RoundingMode.HALF_UP),
                                    litresWithKm.signum() == 0 ? null : km.divide(litresWithKm, 2, java.math.RoundingMode.HALF_UP), rs.getLong(8));
                            return 1;
                        }).list();
                b.totals("Total", "", "", t[0], t[1], t[2], "", "", "");
                return b.build();
            }
        };
    }

    @Bean
    ReportProvider fuelVarianceReport() {
        return new Base("fuel-variance", "Fuel Variance", "Station litres versus sensor-detected litres per transaction, with anomalies.", Permissions.FUEL_READ) {
            public ReportTable build(ReportParams p) {
                LocalDate from = from(p, 29), to = to(p);
                ReportTable.Builder b = ReportTable.builder(code, title).meta("From", from).meta("To", to)
                        .text("date", "Date").text("plate", "Plate").text("driver", "Driver").text("station", "Station").number("litres", "Station litres")
                        .number("sensor", "Sensor litres").number("variance", "Variance").number("l100", "L/100 km").text("anomaly", "Anomaly").text("reason", "Reason");
                bind("""
                        SELECT f.transaction_at, v.plate_number, d.first_name || ' ' || d.last_name, f.station_name, f.litres, f.sensor_detected_litres, f.variance_litres,
                               f.consumption_l_per_100km, f.anomaly, f.anomaly_reason
                        FROM fuel_transactions f JOIN vehicles v ON v.id = f.vehicle_id LEFT JOIN drivers d ON d.id = f.driver_id
                        WHERE f.archived = FALSE AND f.transaction_at >= :start AND f.transaction_at < :end
                          AND (:vehicleId::bigint IS NULL OR f.vehicle_id = :vehicleId::bigint) AND (:driverId::bigint IS NULL OR f.driver_id = :driverId::bigint)
                          AND (:categoryId::bigint IS NULL OR v.category_id = :categoryId::bigint)
                          AND (f.sensor_detected_litres IS NOT NULL OR f.anomaly)
                        ORDER BY f.transaction_at DESC""", p, from, to)
                        .query((rs, i) -> { b.row(ts(rs, 1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getBigDecimal(5), rs.getBigDecimal(6), rs.getBigDecimal(7),
                                rs.getBigDecimal(8), rs.getBoolean(9) ? "Yes" : "No", rs.getString(10)); return 1; }).list();
                return b.build();
            }
        };
    }

    @Bean
    ReportProvider vehicleCostReport() {
        return new Base("vehicle-cost", "Vehicle Operational Cost", "Fuel, maintenance, expenses and fines per vehicle with cost per km.", Permissions.FINANCE_READ) {
            public ReportTable build(ReportParams p) {
                LocalDate from = from(p, 29), to = to(p);
                ReportTable.Builder b = ReportTable.builder(code, title).meta("From", from).meta("To", to)
                        .text("plate", "Plate").text("category", "Category").number("fuel", "Fuel").number("maintenance", "Maintenance").number("expenses", "Expenses")
                        .number("fines", "Fines").number("total", "Total").number("km", "Distance (km)").number("perKm", "Cost / km");
                BigDecimal[] t = {BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO};
                bind("""
                        SELECT v.plate_number, c.name,
                          (SELECT COALESCE(SUM(f.total_amount),0) FROM fuel_transactions f WHERE f.vehicle_id = v.id AND f.archived = FALSE AND f.transaction_at >= :start AND f.transaction_at < :end),
                          (SELECT COALESCE(SUM(m.total_cost),0) FROM maintenance_records m WHERE m.vehicle_id = v.id AND m.status <> 'CANCELLED' AND COALESCE(m.completed_at, m.reported_at) >= :start AND COALESCE(m.completed_at, m.reported_at) < :end),
                          (SELECT COALESCE(SUM(e.amount),0) FROM expenses e WHERE e.vehicle_id = v.id AND e.status IN ('APPROVED','PAID') AND e.incurred_on BETWEEN :from::date AND :to::date),
                          (SELECT COALESCE(SUM(fi.amount),0) FROM traffic_fines fi WHERE fi.vehicle_id = v.id AND fi.status <> 'WAIVED' AND fi.issued_at >= :start AND fi.issued_at < :end),
                          (SELECT COALESCE(SUM(tr.distance_km),0) FROM trips tr WHERE tr.vehicle_id = v.id AND tr.status = 'COMPLETED' AND tr.ended_at >= :start AND tr.ended_at < :end)
                        FROM vehicles v JOIN vehicle_categories c ON c.id = v.category_id
                        WHERE v.archived = FALSE AND (:categoryId::bigint IS NULL OR v.category_id = :categoryId::bigint) AND (:vehicleId::bigint IS NULL OR v.id = :vehicleId::bigint)
                        ORDER BY v.plate_number""", p, from, to)
                        .query((rs, i) -> {
                            BigDecimal fuel = rs.getBigDecimal(3), maint = rs.getBigDecimal(4), exp = rs.getBigDecimal(5), fines = rs.getBigDecimal(6), km = rs.getBigDecimal(7);
                            BigDecimal total = fuel.add(maint).add(exp).add(fines);
                            t[0] = t[0].add(fuel); t[1] = t[1].add(maint); t[2] = t[2].add(exp); t[3] = t[3].add(fines); t[4] = t[4].add(km);
                            b.row(rs.getString(1), rs.getString(2), fuel, maint, exp, fines, total, km, km.signum() == 0 ? null : total.divide(km, 2, java.math.RoundingMode.HALF_UP));
                            return 1;
                        }).list();
                BigDecimal grand = t[0].add(t[1]).add(t[2]).add(t[3]);
                b.totals("Total", "", t[0], t[1], t[2], t[3], grand, t[4], t[4].signum() == 0 ? "" : grand.divide(t[4], 2, java.math.RoundingMode.HALF_UP));
                return b.build();
            }
        };
    }

    @Bean
    ReportProvider incidentReport() {
        return new Base("incidents", "Incident Report", "Incidents and accidents in the period with severity, status and cost.", Permissions.INCIDENT_READ) {
            public ReportTable build(ReportParams p) {
                LocalDate from = from(p, 89), to = to(p);
                ReportTable.Builder b = ReportTable.builder(code, title).meta("From", from).meta("To", to)
                        .text("number", "Incident").text("date", "Date").text("plate", "Plate").text("driver", "Driver").text("type", "Type").text("severity", "Severity")
                        .text("location", "Location").text("description", "Description").number("cost", "Est. cost").text("status", "Status").text("resolved", "Resolved");
                bind("""
                        SELECT i.incident_number, i.occurred_at, v.plate_number, d.first_name || ' ' || d.last_name, i.incident_type, i.severity, i.location, i.description,
                               i.estimated_cost, i.status, i.resolved_at
                        FROM incidents i JOIN vehicles v ON v.id = i.vehicle_id LEFT JOIN drivers d ON d.id = i.driver_id
                        WHERE i.occurred_at >= :start AND i.occurred_at < :end AND (:vehicleId::bigint IS NULL OR i.vehicle_id = :vehicleId::bigint)
                          AND (:driverId::bigint IS NULL OR i.driver_id = :driverId::bigint) AND (:status::varchar IS NULL OR i.status = :status::varchar)
                        ORDER BY i.occurred_at DESC""", p, from, to)
                        .query((rs, i) -> { b.row(rs.getString(1), ts(rs, 2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7),
                                rs.getString(8), rs.getBigDecimal(9), rs.getString(10), ts(rs, 11)); return 1; }).list();
                return b.build();
            }
        };
    }

    @Bean
    ReportProvider upcomingServiceReport() {
        return new Base("upcoming-service", "Upcoming & Overdue Service", "Preventive maintenance schedules that are due soon or overdue.", null) {
            public ReportTable build(ReportParams p) {
                ReportTable.Builder b = ReportTable.builder(code, title).meta("Date", LocalDate.now(clock))
                        .text("plate", "Plate").text("vehicle", "Vehicle").text("service", "Service").number("odometer", "Odometer").number("nextKm", "Next service km")
                        .number("kmLeft", "km remaining").text("nextDate", "Next service date").number("daysLeft", "Days remaining").text("status", "Status");
                bind("""
                        SELECT v.plate_number, v.make || ' ' || v.model, t.name, v.odometer_km, s.next_service_odometer,
                               s.next_service_odometer - v.odometer_km, s.next_service_date, s.next_service_date - :to::date, s.status
                        FROM maintenance_schedules s JOIN vehicles v ON v.id = s.vehicle_id JOIN service_types t ON t.id = s.service_type_id
                        WHERE s.active AND v.archived = FALSE AND (:status::varchar IS NULL OR s.status = :status::varchar)
                          AND (:vehicleId::bigint IS NULL OR s.vehicle_id = :vehicleId::bigint) AND (:categoryId::bigint IS NULL OR v.category_id = :categoryId::bigint)
                          AND (:status::varchar IS NOT NULL OR s.status IN ('DUE_SOON','OVERDUE'))
                        ORDER BY CASE s.status WHEN 'OVERDUE' THEN 0 WHEN 'DUE_SOON' THEN 1 ELSE 2 END, s.next_service_date NULLS LAST""", p, LocalDate.now(clock), LocalDate.now(clock))
                        .query((rs, i) -> { b.row(rs.getString(1), rs.getString(2), rs.getString(3), rs.getLong(4), rs.getObject(5), rs.getObject(6), date(rs, 7), rs.getObject(8), rs.getString(9)); return 1; }).list();
                return b.build();
            }
        };
    }

    @Bean
    ReportProvider technicalReport() {
        return new Base("technical", "Technical Report", "Parts and work performed per maintenance job (voucher, vehicle, particulars, quantity, unit cost, amount, observation).", null) {
            public ReportTable build(ReportParams p) {
                LocalDate from = from(p, 29), to = to(p);
                ReportTable.Builder b = ReportTable.builder(code, title).meta("From", from).meta("To", to)
                        .text("voucher", "Vch / Job").text("date", "Date").text("plate", "Plate").text("issue", "Issue").text("particulars", "Particulars")
                        .number("qty", "Qty").number("cpu", "Unit cost").number("amount", "Amount").text("observation", "Observation").text("comment", "Comment");
                BigDecimal[] total = {BigDecimal.ZERO};
                bind("""
                        SELECT m.maintenance_number, m.reported_at, v.plate_number, m.complaint, x.particulars, x.qty, x.cpu, x.amount, x.observation, x.comment
                        FROM maintenance_records m JOIN vehicles v ON v.id = m.vehicle_id
                        JOIN LATERAL (
                            SELECT p.part_name AS particulars, p.quantity::numeric AS qty, p.unit_cost AS cpu, p.line_total AS amount,
                                   CASE p.status WHEN 'REJECTED' THEN 'Rejected' WHEN 'REQUESTED' THEN 'Pending' ELSE 'Approved' END AS observation, p.rejection_reason AS comment
                            FROM maintenance_parts p WHERE p.maintenance_record_id = m.id
                            UNION ALL
                            SELECT t.description, 1, t.labor_cost, t.labor_cost, CASE t.status WHEN 'DONE' THEN 'Approved' WHEN 'SKIPPED' THEN 'Recheck' ELSE 'Pending' END, t.notes
                            FROM maintenance_tasks t WHERE t.maintenance_record_id = m.id
                        ) x ON TRUE
                        WHERE m.status <> 'CANCELLED' AND m.reported_at >= :start AND m.reported_at < :end
                          AND (:vehicleId::bigint IS NULL OR m.vehicle_id = :vehicleId::bigint) AND (:categoryId::bigint IS NULL OR v.category_id = :categoryId::bigint)
                        ORDER BY m.reported_at DESC, m.maintenance_number""", p, from, to)
                        .query((rs, i) -> { total[0] = total[0].add(rs.getBigDecimal(8) == null ? BigDecimal.ZERO : rs.getBigDecimal(8));
                                b.row(rs.getString(1), ts(rs, 2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getBigDecimal(6), rs.getBigDecimal(7), rs.getBigDecimal(8), rs.getString(9), rs.getString(10)); return 1; }).list();
                b.totals("Total", "", "", "", "", "", "", total[0], "", "");
                return b.build();
            }
        };
    }

    @Bean
    ReportProvider fuelGslReport() {
        return new Base("fuel-gsl", "Fuel GSL Report", "Fuel issued per deployment/voucher: vehicle, deployment, owner, account manager, station (agence), amount and supplier.", Permissions.FUEL_READ) {
            public ReportTable build(ReportParams p) {
                LocalDate from = from(p, 29), to = to(p);
                ReportTable.Builder b = ReportTable.builder(code, title).meta("From", from).meta("To", to)
                        .text("vch", "Vch").text("date", "Date").text("plate", "Plate").text("deployment", "Deployment").text("receipt", "NFR / Receipt").text("owner", "Owner")
                        .text("manager", "Manager").text("agence", "Agence (station)").number("litres", "Litres").number("amount", "Amount").text("supplier", "Supplier").text("comment", "Comment");
                BigDecimal[] t = {BigDecimal.ZERO, BigDecimal.ZERO};
                bind("""
                        SELECT COALESCE(dv.voucher_number, '-'), f.transaction_at, v.plate_number, COALESCE(bk.booking_number || ' · ' || cu.name, '-'), f.receipt_number,
                               COALESCE(v.owner_name, :company::varchar), COALESCE(u.first_name || ' ' || u.last_name, '-'), f.station_name, f.litres, f.total_amount,
                               COALESCE(f.supplier_name, f.station_name), f.notes
                        FROM fuel_transactions f JOIN vehicles v ON v.id = f.vehicle_id
                        LEFT JOIN booking_slots s ON s.id = f.booking_slot_id LEFT JOIN deployment_vouchers dv ON dv.booking_slot_id = s.id
                        LEFT JOIN bookings bk ON bk.id = s.booking_id LEFT JOIN customers cu ON cu.id = bk.customer_id LEFT JOIN users u ON u.id = dv.account_manager_user_id
                        WHERE f.archived = FALSE AND f.transaction_at >= :start AND f.transaction_at < :end
                          AND (:vehicleId::bigint IS NULL OR f.vehicle_id = :vehicleId::bigint) AND (:categoryId::bigint IS NULL OR v.category_id = :categoryId::bigint)
                          AND (:customerId::bigint IS NULL OR bk.customer_id = :customerId::bigint)
                        ORDER BY f.transaction_at DESC""", p, from, to).param("company", "LIMOZ Rwanda")
                        .query((rs, i) -> { t[0] = t[0].add(rs.getBigDecimal(9)); t[1] = t[1].add(rs.getBigDecimal(10));
                                b.row(rs.getString(1), ts(rs, 2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7), rs.getString(8),
                                        rs.getBigDecimal(9), rs.getBigDecimal(10), rs.getString(11), rs.getString(12)); return 1; }).list();
                b.totals("Total", "", "", "", "", "", "", "", t[0], t[1], "", "");
                return b.build();
            }
        };
    }
}
