package com.limoz.fleet.dashboard;

import com.limoz.fleet.config.CacheConfig;
import com.limoz.fleet.dashboard.dto.AlertDigest;
import com.limoz.fleet.dashboard.dto.CountByLabel;
import com.limoz.fleet.dashboard.dto.DailyPoint;
import com.limoz.fleet.dashboard.dto.DashboardSummary;
import com.limoz.fleet.dashboard.dto.DeploymentToday;
import com.limoz.fleet.dashboard.dto.FleetStatusResponse;
import com.limoz.fleet.dashboard.dto.FuelTrendResponse;
import com.limoz.fleet.dashboard.dto.MaintenanceDashboardResponse;
import com.limoz.fleet.dashboard.dto.RecentBooking;
import com.limoz.fleet.dashboard.dto.UtilizationResponse;
import com.limoz.fleet.dashboard.dto.VehicleCost;
import com.limoz.fleet.settings.SettingKeys;
import com.limoz.fleet.settings.SettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Executive dashboard. Every number comes from aggregate SQL over operational tables; results are cached for
 * 60 seconds per parameter set and evicted when vehicles/drivers/assignments change.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardService {

    private static final String ACTIVE_VEHICLES = "FROM vehicles v WHERE v.archived = FALSE";

    private final DashboardQueries q;
    private final SettingsService settings;
    private final Clock clock;

    @Cacheable(cacheNames = CacheConfig.DASHBOARD, key = "'summary:' + #date + ':' + #categoryId + ':' + #department")
    public DashboardSummary summary(LocalDate date, Long categoryId, String department) {
        LocalDate day = date == null ? LocalDate.now(clock) : date;
        Map<String, Object> p = new HashMap<>();
        p.put("start", q.startOf(day));
        p.put("end", q.endOf(day));
        p.put("monthStart", q.startOf(day.withDayOfMonth(1)));
        p.put("weekEnd", q.endOf(day.plusDays(7)));
        p.put("day", day);
        p.put("categoryId", categoryId);
        p.put("department", department);
        String scope = ACTIVE_VEHICLES + " AND (:categoryId::bigint IS NULL OR v.category_id = :categoryId::bigint) AND (:department::varchar IS NULL OR v.department = :department::varchar)";

        long total = q.count("SELECT COUNT(*) " + scope, p);
        long available = q.count("SELECT COUNT(*) " + scope + " AND v.operational_status = 'AVAILABLE'", p);
        long assigned = q.count("SELECT COUNT(*) " + scope + " AND v.operational_status = 'ASSIGNED'", p);
        long onTrip = q.count("SELECT COUNT(*) " + scope + " AND v.operational_status = 'ON_TRIP'", p);
        long reserved = q.count("SELECT COUNT(*) " + scope + " AND v.operational_status = 'RESERVED'", p);
        long inWorkshop = q.count("SELECT COUNT(*) " + scope + " AND v.operational_status = 'IN_MAINTENANCE'", p);
        long outOfService = q.count("SELECT COUNT(*) " + scope + " AND v.operational_status IN ('OUT_OF_SERVICE','INACTIVE')", p);
        long idle = q.count("SELECT COUNT(*) " + scope + """
                 AND v.operational_status IN ('AVAILABLE','ASSIGNED','RESERVED')
                 AND NOT EXISTS (SELECT 1 FROM trips t WHERE t.vehicle_id = v.id AND t.started_at >= :start AND t.started_at < :end)
                 AND NOT EXISTS (SELECT 1 FROM daily_movement_summaries m WHERE m.vehicle_id = v.id AND m.summary_date = :day::date AND m.moved = TRUE)
                 AND NOT EXISTS (SELECT 1 FROM booking_slots s WHERE s.vehicle_id = v.id AND s.status = 'DEPLOYED')""", p);
        long gpsProblems = q.count("SELECT COUNT(*) " + scope + " AND EXISTS (SELECT 1 FROM telematics_devices d WHERE d.vehicle_id = v.id AND d.active AND d.gps_status IN ('OFFLINE','NO_SIGNAL','DISCONNECTED'))", p);
        long fuelSensorProblems = q.count("SELECT COUNT(*) " + scope + " AND EXISTS (SELECT 1 FROM telematics_devices d WHERE d.vehicle_id = v.id AND d.active AND d.fuel_sensor_status = 'FAULTY')", p);
        long tripsToday = q.count("SELECT COUNT(*) FROM trips t JOIN vehicles v ON v.id = t.vehicle_id WHERE t.status <> 'CANCELLED' AND COALESCE(t.started_at, t.scheduled_start_at) >= :start AND COALESCE(t.started_at, t.scheduled_start_at) < :end AND (:categoryId::bigint IS NULL OR v.category_id = :categoryId::bigint) AND (:department::varchar IS NULL OR v.department = :department::varchar)", p);
        BigDecimal distanceToday = q.sum("SELECT COALESCE(SUM(m.distance_km),0) FROM daily_movement_summaries m JOIN vehicles v ON v.id = m.vehicle_id WHERE m.summary_date = :day::date AND (:categoryId::bigint IS NULL OR v.category_id = :categoryId::bigint) AND (:department::varchar IS NULL OR v.department = :department::varchar)", p);
        if (distanceToday.signum() == 0) {
            distanceToday = q.sum("SELECT COALESCE(SUM(t.distance_km),0) FROM trips t JOIN vehicles v ON v.id = t.vehicle_id WHERE t.status = 'COMPLETED' AND t.ended_at >= :start AND t.ended_at < :end AND (:categoryId::bigint IS NULL OR v.category_id = :categoryId::bigint) AND (:department::varchar IS NULL OR v.department = :department::varchar)", p);
        }
        long totalDrivers = q.count("SELECT COUNT(*) FROM drivers WHERE archived = FALSE", p);
        long activeDrivers = q.count("SELECT COUNT(*) FROM drivers WHERE archived = FALSE AND status IN ('ASSIGNED','ON_TRIP')", p);
        long availableDrivers = q.count("SELECT COUNT(*) FROM drivers WHERE archived = FALSE AND status = 'AVAILABLE'", p);
        long dueForService = q.count("SELECT COUNT(DISTINCT s.vehicle_id) FROM maintenance_schedules s JOIN vehicles v ON v.id = s.vehicle_id WHERE s.active AND s.status = 'DUE_SOON' AND v.archived = FALSE", p);
        long overdue = q.count("SELECT COUNT(DISTINCT s.vehicle_id) FROM maintenance_schedules s JOIN vehicles v ON v.id = s.vehicle_id WHERE s.active AND s.status = 'OVERDUE' AND v.archived = FALSE", p);
        long openJobs = q.count("SELECT COUNT(*) FROM maintenance_records WHERE status NOT IN ('COMPLETED','RELEASED','CANCELLED')", p);
        BigDecimal fuelLitres = q.sum("SELECT COALESCE(SUM(litres),0) FROM fuel_transactions WHERE archived = FALSE AND transaction_at >= :monthStart AND transaction_at < :end", p);
        BigDecimal fuelCost = q.sum("SELECT COALESCE(SUM(total_amount),0) FROM fuel_transactions WHERE archived = FALSE AND transaction_at >= :monthStart AND transaction_at < :end", p);
        long upcomingBookings = q.count("SELECT COUNT(*) FROM bookings WHERE status IN ('REQUESTED','CONFIRMED','READY_FOR_DEPLOYMENT') AND start_date >= :day::date AND start_date <= :day::date + 7", p);
        long awaitingDispatch = q.count("SELECT COUNT(DISTINCT b.id) FROM bookings b JOIN booking_slots s ON s.booking_id = b.id WHERE b.status IN ('CONFIRMED','READY_FOR_DEPLOYMENT') AND s.status = 'UNASSIGNED'", p);
        long activeIncidents = q.count("SELECT COUNT(*) FROM incidents WHERE status IN ('OPEN','UNDER_INVESTIGATION')", p);
        long expiredDocs = q.count("SELECT COUNT(*) FROM vehicle_documents d JOIN vehicles v ON v.id = d.vehicle_id WHERE d.superseded = FALSE AND v.archived = FALSE AND d.status = 'EXPIRED'", p);
        long expiringDocs = q.count("SELECT COUNT(*) FROM vehicle_documents d JOIN vehicles v ON v.id = d.vehicle_id WHERE d.superseded = FALSE AND v.archived = FALSE AND d.status = 'EXPIRING_SOON'", p);
        long unpaidFines = q.count("SELECT COUNT(*) FROM traffic_fines WHERE status = 'UNPAID'", p);
        long overdueInvoices = q.count("SELECT COUNT(*) FROM invoices WHERE status = 'OVERDUE' OR (status IN ('ISSUED','PARTIALLY_PAID') AND due_date < :day::date)", p);
        BigDecimal receivables = q.sum("SELECT COALESCE(SUM(total_amount - amount_paid),0) FROM invoices WHERE status IN ('ISSUED','PARTIALLY_PAID','OVERDUE')", p);
        long activeAlerts = q.count("SELECT COUNT(*) FROM alerts WHERE status IN ('ACTIVE','ACKNOWLEDGED')", p);
        long criticalAlerts = q.count("SELECT COUNT(*) FROM alerts WHERE status IN ('ACTIVE','ACKNOWLEDGED') AND severity = 'CRITICAL'", p);
        long used = q.count("SELECT COUNT(DISTINCT x.vehicle_id) FROM (SELECT vehicle_id FROM trips WHERE status <> 'CANCELLED' AND COALESCE(started_at, scheduled_start_at) >= :start AND COALESCE(started_at, scheduled_start_at) < :end UNION SELECT vehicle_id FROM booking_slots WHERE vehicle_id IS NOT NULL AND status IN ('DEPLOYED','RETURNED') AND start_date <= :day::date AND end_date >= :day::date) x", p);
        long dispatchable = available + assigned + onTrip + reserved;
        BigDecimal utilisation = dispatchable == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(used * 100.0 / dispatchable).setScale(1, RoundingMode.HALF_UP);

        return new DashboardSummary(day, total, available, assigned, onTrip, reserved, inWorkshop, outOfService, idle, gpsProblems,
                fuelSensorProblems, tripsToday, distanceToday, totalDrivers, activeDrivers, availableDrivers, dueForService, overdue,
                openJobs, fuelLitres, fuelCost, upcomingBookings, awaitingDispatch, activeIncidents, expiredDocs, expiringDocs,
                unpaidFines, overdueInvoices, receivables, activeAlerts, criticalAlerts, utilisation);
    }

    @Cacheable(cacheNames = CacheConfig.DASHBOARD, key = "'fleet-status'")
    public FleetStatusResponse fleetStatus() {
        Map<String, Object> p = Map.of();
        long total = q.count("SELECT COUNT(*) " + ACTIVE_VEHICLES, p);
        return new FleetStatusResponse(total,
                counts(q.labelCounts("SELECT v.operational_status, COUNT(*) " + ACTIVE_VEHICLES + " GROUP BY 1 ORDER BY 1", p)),
                counts(q.labelCounts("SELECT c.name, COUNT(*) FROM vehicles v JOIN vehicle_categories c ON c.id = v.category_id WHERE v.archived = FALSE GROUP BY 1 ORDER BY 1", p)),
                counts(q.labelCounts("SELECT v.maintenance_status, COUNT(*) " + ACTIVE_VEHICLES + " GROUP BY 1 ORDER BY 1", p)));
    }

    @Cacheable(cacheNames = CacheConfig.DASHBOARD, key = "'utilization:' + #from + ':' + #to + ':' + #categoryId")
    public UtilizationResponse utilization(LocalDate from, LocalDate to, Long categoryId) {
        LocalDate end = to == null ? LocalDate.now(clock) : to;
        LocalDate start = from == null ? end.minusDays(29) : from;
        long days = ChronoUnit.DAYS.between(start, end) + 1;
        Map<String, Object> p = new HashMap<>();
        p.put("from", start);
        p.put("to", end);
        p.put("start", q.startOf(start));
        p.put("end", q.endOf(end));
        p.put("categoryId", categoryId);
        long fleet = q.count("SELECT COUNT(*) " + ACTIVE_VEHICLES + " AND v.operational_status NOT IN ('OUT_OF_SERVICE','INACTIVE') AND (:categoryId::bigint IS NULL OR v.category_id = :categoryId::bigint)", p);
        // vehicle-days used: union of trip days and deployed slot days
        String usage = """
                WITH usage_days AS (
                    SELECT t.vehicle_id, %s AS day FROM trips t WHERE t.status <> 'CANCELLED'
                        AND COALESCE(t.started_at, t.scheduled_start_at) >= :start AND COALESCE(t.started_at, t.scheduled_start_at) < :end
                    UNION
                    SELECT s.vehicle_id, g.day::date FROM booking_slots s
                        CROSS JOIN LATERAL generate_series(GREATEST(s.start_date, :from::date), LEAST(s.end_date, :to::date), interval '1 day') AS g(day)
                        WHERE s.vehicle_id IS NOT NULL AND s.status IN ('DEPLOYED','RETURNED') AND s.start_date <= :to::date AND s.end_date >= :from::date
                )
                """.formatted(q.localDate("COALESCE(t.started_at, t.scheduled_start_at)"));
        List<DashboardQueries.DayValue> daily = q.dayValues(usage + """
                SELECT d.day::date, COUNT(DISTINCT u.vehicle_id), 0
                FROM generate_series(:from::date, :to::date, interval '1 day') AS d(day)
                LEFT JOIN usage_days u ON u.day = d.day::date
                LEFT JOIN vehicles v ON v.id = u.vehicle_id AND (:categoryId::bigint IS NULL OR v.category_id = :categoryId::bigint)
                GROUP BY d.day ORDER BY d.day""", p);
        List<DailyPoint> points = new ArrayList<>();
        BigDecimal sumPct = BigDecimal.ZERO;
        for (DashboardQueries.DayValue d : daily) {
            BigDecimal pct = fleet == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(d.count() * 100.0 / fleet).setScale(1, RoundingMode.HALF_UP);
            sumPct = sumPct.add(pct);
            points.add(new DailyPoint(d.day(), d.count(), pct));
        }
        BigDecimal avg = points.isEmpty() ? BigDecimal.ZERO : sumPct.divide(BigDecimal.valueOf(points.size()), 1, RoundingMode.HALF_UP);
        int high = settings.getInt(SettingKeys.UTILISATION_HIGH_PERCENT);
        int low = settings.getInt(SettingKeys.UTILISATION_LOW_PERCENT);
        List<UtilizationResponse.VehicleUtilization> vehicles = q.list(usage + """
                SELECT v.id, v.plate_number, c.name,
                       (SELECT COUNT(DISTINCT u.day) FROM usage_days u WHERE u.vehicle_id = v.id) AS days_used,
                       (SELECT COALESCE(SUM(t.distance_km),0) FROM trips t WHERE t.vehicle_id = v.id AND t.status = 'COMPLETED' AND t.ended_at >= :start AND t.ended_at < :end) AS distance
                FROM vehicles v JOIN vehicle_categories c ON c.id = v.category_id
                WHERE v.archived = FALSE AND (:categoryId::bigint IS NULL OR v.category_id = :categoryId::bigint)
                ORDER BY days_used DESC, v.plate_number""", p, (rs, i) -> {
            long used = rs.getLong(4);
            BigDecimal pct = BigDecimal.valueOf(used * 100.0 / days).setScale(1, RoundingMode.HALF_UP);
            String cls = pct.intValue() >= high ? "HEAVY" : pct.intValue() < low ? "UNDER_UTILISED" : "NORMAL";
            return new UtilizationResponse.VehicleUtilization(rs.getLong(1), rs.getString(2), rs.getString(3), used, days, pct,
                    DashboardQueries.nz(rs.getBigDecimal(5)), cls);
        });
        return new UtilizationResponse(start, end, fleet, avg, points, vehicles);
    }

    @Cacheable(cacheNames = CacheConfig.DASHBOARD, key = "'fuel:' + #from + ':' + #to + ':' + #vehicleId + ':' + #categoryId")
    public FuelTrendResponse fuelTrend(LocalDate from, LocalDate to, Long vehicleId, Long categoryId) {
        LocalDate end = to == null ? LocalDate.now(clock) : to;
        LocalDate start = from == null ? end.minusDays(29) : from;
        Map<String, Object> p = new HashMap<>();
        p.put("start", q.startOf(start));
        p.put("end", q.endOf(end));
        p.put("vehicleId", vehicleId);
        p.put("categoryId", categoryId);
        p.put("from", start);
        p.put("to", end);
        String where = "FROM fuel_transactions f JOIN vehicles v ON v.id = f.vehicle_id WHERE f.archived = FALSE AND f.transaction_at >= :start AND f.transaction_at < :end"
                + " AND (:vehicleId::bigint IS NULL OR f.vehicle_id = :vehicleId::bigint) AND (:categoryId::bigint IS NULL OR v.category_id = :categoryId::bigint)";
        BigDecimal litres = q.sum("SELECT COALESCE(SUM(f.litres),0) " + where, p);
        BigDecimal cost = q.sum("SELECT COALESCE(SUM(f.total_amount),0) " + where, p);
        BigDecimal distance = q.sum("SELECT COALESCE(SUM(f.distance_since_last_km),0) " + where + " AND f.distance_since_last_km IS NOT NULL", p);
        BigDecimal litresWithDistance = q.sum("SELECT COALESCE(SUM(f.litres),0) " + where + " AND f.distance_since_last_km IS NOT NULL AND f.distance_since_last_km > 0", p);
        BigDecimal avg = distance.signum() == 0 ? BigDecimal.ZERO : litresWithDistance.multiply(BigDecimal.valueOf(100)).divide(distance, 2, RoundingMode.HALF_UP);
        long anomalies = q.count("SELECT COUNT(*) " + where + " AND f.anomaly = TRUE", p);
        List<DailyPoint> daily = q.dayValues("SELECT d.day::date, COUNT(f.id), COALESCE(SUM(f.litres),0) FROM generate_series(:from::date, :to::date, interval '1 day') AS d(day) LEFT JOIN fuel_transactions f ON "
                + q.localDate("f.transaction_at") + " = d.day::date AND f.archived = FALSE AND (:vehicleId::bigint IS NULL OR f.vehicle_id = :vehicleId::bigint) LEFT JOIN vehicles v ON v.id = f.vehicle_id AND (:categoryId::bigint IS NULL OR v.category_id = :categoryId::bigint) GROUP BY d.day ORDER BY d.day", p)
                .stream().map(d -> new DailyPoint(d.day(), d.count(), d.value())).toList();
        List<FuelTrendResponse.VehicleFuel> top = q.list("SELECT v.id, v.plate_number, SUM(f.litres), SUM(f.total_amount), COALESCE(SUM(f.distance_since_last_km),0) " + where
                + " GROUP BY v.id, v.plate_number ORDER BY SUM(f.litres) DESC LIMIT 10", p, (rs, i) -> {
            BigDecimal l = DashboardQueries.nz(rs.getBigDecimal(3));
            BigDecimal d = DashboardQueries.nz(rs.getBigDecimal(5));
            BigDecimal per100 = d.signum() == 0 ? null : l.multiply(BigDecimal.valueOf(100)).divide(d, 2, RoundingMode.HALF_UP);
            return new FuelTrendResponse.VehicleFuel(rs.getLong(1), rs.getString(2), l, DashboardQueries.nz(rs.getBigDecimal(4)), d, per100);
        });
        return new FuelTrendResponse(start, end, litres, cost, avg, anomalies, daily, top);
    }

    @Cacheable(cacheNames = CacheConfig.DASHBOARD, key = "'maintenance:' + #from + ':' + #to")
    public MaintenanceDashboardResponse maintenance(LocalDate from, LocalDate to) {
        LocalDate end = to == null ? LocalDate.now(clock) : to;
        LocalDate start = from == null ? end.minusMonths(6).withDayOfMonth(1) : from;
        Map<String, Object> p = new HashMap<>();
        p.put("start", q.startOf(start));
        p.put("end", q.endOf(end));
        long open = q.count("SELECT COUNT(*) FROM maintenance_records WHERE status NOT IN ('COMPLETED','RELEASED','CANCELLED')", p);
        long inWorkshop = q.count("SELECT COUNT(*) FROM maintenance_records WHERE status IN ('IN_PROGRESS','WAITING_FOR_PARTS')", p);
        long waiting = q.count("SELECT COUNT(*) FROM maintenance_records WHERE status = 'WAITING_FOR_PARTS'", p);
        long completed = q.count("SELECT COUNT(*) FROM maintenance_records WHERE status IN ('COMPLETED','RELEASED') AND completed_at >= :start AND completed_at < :end", p);
        BigDecimal cost = q.sum("SELECT COALESCE(SUM(total_cost),0) FROM maintenance_records WHERE status IN ('COMPLETED','RELEASED') AND completed_at >= :start AND completed_at < :end", p);
        long dueSoon = q.count("SELECT COUNT(*) FROM maintenance_schedules WHERE active AND status = 'DUE_SOON'", p);
        long overdue = q.count("SELECT COUNT(*) FROM maintenance_schedules WHERE active AND status = 'OVERDUE'", p);
        List<CountByLabel> byStatus = counts(q.labelCounts("SELECT status, COUNT(*) FROM maintenance_records WHERE reported_at >= :start AND reported_at < :end GROUP BY 1 ORDER BY 1", p));
        List<CountByLabel> byType = counts(q.labelCounts("SELECT maintenance_type, COUNT(*), COALESCE(SUM(total_cost),0) FROM maintenance_records WHERE reported_at >= :start AND reported_at < :end AND status <> 'CANCELLED' GROUP BY 1 ORDER BY 1", p));
        List<DailyPoint> monthly = q.dayValues("SELECT date_trunc('month', " + q.localDate("COALESCE(completed_at, reported_at)") + ")::date, COUNT(*), COALESCE(SUM(total_cost),0) FROM maintenance_records WHERE status <> 'CANCELLED' AND COALESCE(completed_at, reported_at) >= :start AND COALESCE(completed_at, reported_at) < :end GROUP BY 1 ORDER BY 1", p)
                .stream().map(d -> new DailyPoint(d.day(), d.count(), d.value())).toList();
        List<CountByLabel> topVehicles = counts(q.labelCounts("SELECT v.plate_number, COUNT(m.id), COALESCE(SUM(m.total_cost),0) FROM maintenance_records m JOIN vehicles v ON v.id = m.vehicle_id WHERE m.status <> 'CANCELLED' AND m.reported_at >= :start AND m.reported_at < :end GROUP BY v.plate_number ORDER BY 3 DESC LIMIT 10", p));
        return new MaintenanceDashboardResponse(start, end, open, inWorkshop, waiting, completed, cost, dueSoon, overdue, byStatus, byType, monthly, topVehicles);
    }

    @Cacheable(cacheNames = CacheConfig.DASHBOARD, key = "'alerts-digest'")
    public AlertDigest alerts() {
        Map<String, Object> p = Map.of();
        long active = q.count("SELECT COUNT(*) FROM alerts WHERE status IN ('ACTIVE','ACKNOWLEDGED')", p);
        long critical = q.count("SELECT COUNT(*) FROM alerts WHERE status IN ('ACTIVE','ACKNOWLEDGED') AND severity = 'CRITICAL'", p);
        long warning = q.count("SELECT COUNT(*) FROM alerts WHERE status IN ('ACTIVE','ACKNOWLEDGED') AND severity = 'WARNING'", p);
        long info = q.count("SELECT COUNT(*) FROM alerts WHERE status IN ('ACTIVE','ACKNOWLEDGED') AND severity = 'INFO'", p);
        List<AlertDigest.Item> items = q.list("""
                SELECT id, alert_type, severity, title, message, entity_type, entity_id, entity_reference, link_path, status, last_detected_at
                FROM alerts WHERE status IN ('ACTIVE','ACKNOWLEDGED')
                ORDER BY CASE severity WHEN 'CRITICAL' THEN 0 WHEN 'WARNING' THEN 1 ELSE 2 END, last_detected_at DESC LIMIT 20""", p,
                (rs, i) -> new AlertDigest.Item(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5),
                        rs.getString(6), rs.getLong(7), rs.getString(8), rs.getString(9), rs.getString(10), rs.getTimestamp(11).toInstant()));
        return new AlertDigest(active, critical, warning, info, items);
    }

    @Cacheable(cacheNames = CacheConfig.DASHBOARD, key = "'trips-per-day:' + #from + ':' + #to + ':' + #vehicleId + ':' + #driverId")
    public List<DailyPoint> tripsPerDay(LocalDate from, LocalDate to, Long vehicleId, Long driverId) {
        LocalDate end = to == null ? LocalDate.now(clock) : to;
        LocalDate start = from == null ? end.minusDays(29) : from;
        Map<String, Object> p = new HashMap<>();
        p.put("from", start);
        p.put("to", end);
        p.put("vehicleId", vehicleId);
        p.put("driverId", driverId);
        return q.dayValues("SELECT d.day::date, COUNT(t.id), COALESCE(SUM(t.distance_km),0) FROM generate_series(:from::date, :to::date, interval '1 day') AS d(day) LEFT JOIN trips t ON "
                + q.localDate("COALESCE(t.started_at, t.scheduled_start_at)") + " = d.day::date AND t.status <> 'CANCELLED' AND (:vehicleId::bigint IS NULL OR t.vehicle_id = :vehicleId::bigint) AND (:driverId::bigint IS NULL OR t.driver_id = :driverId::bigint) GROUP BY d.day ORDER BY d.day", p)
                .stream().map(d -> new DailyPoint(d.day(), d.count(), d.value())).toList();
    }

    @Cacheable(cacheNames = CacheConfig.DASHBOARD, key = "'distance-trend:' + #from + ':' + #to + ':' + #vehicleId + ':' + #categoryId")
    public List<DailyPoint> distanceTrend(LocalDate from, LocalDate to, Long vehicleId, Long categoryId) {
        LocalDate end = to == null ? LocalDate.now(clock) : to;
        LocalDate start = from == null ? end.minusDays(29) : from;
        Map<String, Object> p = new HashMap<>();
        p.put("from", start);
        p.put("to", end);
        p.put("vehicleId", vehicleId);
        p.put("categoryId", categoryId);
        List<DashboardQueries.DayValue> movement = q.dayValues("""
                SELECT d.day::date, COUNT(m.id) FILTER (WHERE m.moved), COALESCE(SUM(m.distance_km),0)
                FROM generate_series(:from::date, :to::date, interval '1 day') AS d(day)
                LEFT JOIN daily_movement_summaries m ON m.summary_date = d.day::date AND (:vehicleId::bigint IS NULL OR m.vehicle_id = :vehicleId::bigint)
                LEFT JOIN vehicles v ON v.id = m.vehicle_id AND (:categoryId::bigint IS NULL OR v.category_id = :categoryId::bigint)
                GROUP BY d.day ORDER BY d.day""", p);
        boolean hasMovement = movement.stream().anyMatch(d -> d.value().signum() > 0);
        if (!hasMovement) {
            movement = q.dayValues("SELECT d.day::date, COUNT(DISTINCT t.vehicle_id), COALESCE(SUM(t.distance_km),0) FROM generate_series(:from::date, :to::date, interval '1 day') AS d(day) LEFT JOIN trips t ON "
                    + q.localDate("t.ended_at") + " = d.day::date AND t.status = 'COMPLETED' AND (:vehicleId::bigint IS NULL OR t.vehicle_id = :vehicleId::bigint) LEFT JOIN vehicles v ON v.id = t.vehicle_id AND (:categoryId::bigint IS NULL OR v.category_id = :categoryId::bigint) GROUP BY d.day ORDER BY d.day", p);
        }
        return movement.stream().map(d -> new DailyPoint(d.day(), d.count(), d.value())).toList();
    }

    @Cacheable(cacheNames = CacheConfig.DASHBOARD, key = "'cost-by-vehicle:' + #from + ':' + #to + ':' + #categoryId")
    public List<VehicleCost> costByVehicle(LocalDate from, LocalDate to, Long categoryId) {
        LocalDate end = to == null ? LocalDate.now(clock) : to;
        LocalDate start = from == null ? end.minusDays(29) : from;
        Map<String, Object> p = new HashMap<>();
        p.put("start", q.startOf(start));
        p.put("end", q.endOf(end));
        p.put("from", start);
        p.put("to", end);
        p.put("categoryId", categoryId);
        return q.list("""
                SELECT v.id, v.plate_number, c.name,
                  (SELECT COALESCE(SUM(f.total_amount),0) FROM fuel_transactions f WHERE f.vehicle_id = v.id AND f.archived = FALSE AND f.transaction_at >= :start AND f.transaction_at < :end) AS fuel,
                  (SELECT COALESCE(SUM(m.total_cost),0) FROM maintenance_records m WHERE m.vehicle_id = v.id AND m.status <> 'CANCELLED' AND COALESCE(m.completed_at, m.reported_at) >= :start AND COALESCE(m.completed_at, m.reported_at) < :end) AS maint,
                  (SELECT COALESCE(SUM(e.amount),0) FROM expenses e WHERE e.vehicle_id = v.id AND e.status IN ('APPROVED','PAID') AND e.incurred_on >= :from::date AND e.incurred_on <= :to::date) AS exp,
                  (SELECT COALESCE(SUM(fi.amount),0) FROM traffic_fines fi WHERE fi.vehicle_id = v.id AND fi.status <> 'WAIVED' AND fi.issued_at >= :start AND fi.issued_at < :end) AS fines,
                  (SELECT COALESCE(SUM(t.distance_km),0) FROM trips t WHERE t.vehicle_id = v.id AND t.status = 'COMPLETED' AND t.ended_at >= :start AND t.ended_at < :end) AS km
                FROM vehicles v JOIN vehicle_categories c ON c.id = v.category_id
                WHERE v.archived = FALSE AND (:categoryId::bigint IS NULL OR v.category_id = :categoryId::bigint)
                ORDER BY (SELECT COALESCE(SUM(f.total_amount),0) FROM fuel_transactions f WHERE f.vehicle_id = v.id AND f.archived = FALSE AND f.transaction_at >= :start AND f.transaction_at < :end)
                       + (SELECT COALESCE(SUM(m.total_cost),0) FROM maintenance_records m WHERE m.vehicle_id = v.id AND m.status <> 'CANCELLED' AND COALESCE(m.completed_at, m.reported_at) >= :start AND COALESCE(m.completed_at, m.reported_at) < :end) DESC, v.plate_number""",
                p, (rs, i) -> {
                    BigDecimal fuel = DashboardQueries.nz(rs.getBigDecimal(4));
                    BigDecimal maint = DashboardQueries.nz(rs.getBigDecimal(5));
                    BigDecimal exp = DashboardQueries.nz(rs.getBigDecimal(6));
                    BigDecimal fines = DashboardQueries.nz(rs.getBigDecimal(7));
                    BigDecimal km = DashboardQueries.nz(rs.getBigDecimal(8));
                    BigDecimal total = fuel.add(maint).add(exp).add(fines);
                    BigDecimal perKm = km.signum() == 0 ? null : total.divide(km, 2, RoundingMode.HALF_UP);
                    return new VehicleCost(rs.getLong(1), rs.getString(2), rs.getString(3), fuel, maint, exp, fines, total, km, perKm);
                });
    }

    @Cacheable(cacheNames = CacheConfig.DASHBOARD, key = "'availability-trend:' + #from + ':' + #to")
    public List<DailyPoint> availabilityTrend(LocalDate from, LocalDate to) {
        LocalDate end = to == null ? LocalDate.now(clock) : to;
        LocalDate start = from == null ? end.minusDays(29) : from;
        Map<String, Object> p = new HashMap<>();
        p.put("from", start);
        p.put("to", end);
        long fleet = q.count("SELECT COUNT(*) " + ACTIVE_VEHICLES + " AND v.operational_status NOT IN ('OUT_OF_SERVICE','INACTIVE')", p);
        // available = fleet - vehicles in the workshop that day - vehicles deployed that day
        return q.dayValues("""
                SELECT d.day::date,
                  (SELECT COUNT(DISTINCT m.vehicle_id) FROM maintenance_records m
                     WHERE m.status <> 'CANCELLED' AND m.started_at IS NOT NULL
                       AND %s <= d.day::date AND COALESCE(%s, d.day::date) >= d.day::date) AS in_workshop,
                  (SELECT COUNT(DISTINCT s.vehicle_id) FROM booking_slots s WHERE s.vehicle_id IS NOT NULL AND s.status IN ('DEPLOYED','RETURNED')
                     AND s.start_date <= d.day::date AND s.end_date >= d.day::date) AS deployed
                FROM generate_series(:from::date, :to::date, interval '1 day') AS d(day) ORDER BY d.day"""
                .formatted(q.localDate("m.started_at"), q.localDate("m.completed_at")), p)
                .stream().map(d -> {
                    long available = Math.max(0, fleet - d.count() - d.value().longValue());
                    BigDecimal pct = fleet == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(available * 100.0 / fleet).setScale(1, RoundingMode.HALF_UP);
                    return new DailyPoint(d.day(), available, pct);
                }).toList();
    }

    @Cacheable(cacheNames = CacheConfig.DASHBOARD, key = "'deployments-today:' + #date")
    public List<DeploymentToday> todaysDeployments(LocalDate date) {
        LocalDate day = date == null ? LocalDate.now(clock) : date;
        return q.list("""
                SELECT s.id, b.id, b.booking_number, c.name, v.plate_number, cat.name, d.first_name || ' ' || d.last_name,
                       COALESCE(b.pickup_location, '') || ' -> ' || COALESCE(b.dropoff_location, ''), s.start_date, s.end_date, s.status, s.departed_at
                FROM booking_slots s JOIN bookings b ON b.id = s.booking_id JOIN customers c ON c.id = b.customer_id
                JOIN vehicle_categories cat ON cat.id = s.category_id
                LEFT JOIN vehicles v ON v.id = s.vehicle_id LEFT JOIN drivers d ON d.id = s.driver_id
                WHERE s.status IN ('ASSIGNED','DEPLOYED') AND s.start_date <= :day::date AND s.end_date >= :day::date AND b.status <> 'CANCELLED'
                ORDER BY s.status DESC, s.start_date, b.booking_number, s.slot_number LIMIT 50""", Map.of("day", day),
                (rs, i) -> new DeploymentToday(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6),
                        rs.getString(7), rs.getString(8), rs.getDate(9).toLocalDate(), rs.getDate(10).toLocalDate(), rs.getString(11),
                        rs.getTimestamp(12) == null ? null : rs.getTimestamp(12).toInstant()));
    }

    @Cacheable(cacheNames = CacheConfig.DASHBOARD, key = "'recent-bookings'")
    public List<RecentBooking> recentBookings() {
        return q.list("""
                SELECT b.id, b.booking_number, c.name, b.service_type, b.start_date, b.end_date, b.status, b.total_amount, b.currency
                FROM bookings b JOIN customers c ON c.id = b.customer_id ORDER BY b.created_at DESC LIMIT 8""", Map.of(),
                (rs, i) -> new RecentBooking(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getDate(5).toLocalDate(),
                        rs.getDate(6).toLocalDate(), rs.getString(7), rs.getBigDecimal(8), rs.getString(9)));
    }

    private static List<CountByLabel> counts(List<DashboardQueries.LabelCount> rows) {
        return rows.stream().map(r -> new CountByLabel(r.label(), r.count(), r.value())).toList();
    }
}
