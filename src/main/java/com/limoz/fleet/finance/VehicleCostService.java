package com.limoz.fleet.finance;

import com.limoz.fleet.finance.dto.VehicleCostResponse;
import com.limoz.fleet.settings.SettingKeys;
import com.limoz.fleet.settings.SettingsService;
import com.limoz.fleet.vehicle.Vehicle;
import com.limoz.fleet.vehicle.VehicleService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * Cost of ownership of a vehicle over a period (the "Costs" tab of the vehicle profile): fuel, maintenance,
 * approved expenses and fines in RWF, plus cost per km using the distance of completed trips. The source
 * tables belong to other modules, so the figures are aggregated directly with read-only queries.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VehicleCostService {

    private final JdbcClient jdbcClient;
    private final VehicleService vehicleService;
    private final SettingsService settingsService;
    private final ZoneId operationalZone;

    public VehicleCostResponse costs(Long vehicleId, LocalDate from, LocalDate to) {
        Vehicle vehicle = vehicleService.load(vehicleId);
        BigDecimal rate = settingsService.getDecimal(SettingKeys.USD_TO_RWF_RATE);
        OffsetDateTime start = from == null ? OffsetDateTime.of(1970, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC)
                : from.atStartOfDay(operationalZone).toInstant().atOffset(ZoneOffset.UTC);
        OffsetDateTime end = to == null ? OffsetDateTime.of(2999, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC)
                : to.plusDays(1).atStartOfDay(operationalZone).toInstant().atOffset(ZoneOffset.UTC);
        LocalDate startDate = from == null ? LocalDate.of(1970, 1, 1) : from;
        LocalDate endDate = to == null ? LocalDate.of(2999, 1, 1) : to;

        BigDecimal fuel = amount("select coalesce(sum(case when currency = 'USD' then total_amount * :rate else total_amount end), 0) "
                + "from fuel_transactions where vehicle_id = :vehicleId and archived = false and transaction_at >= :from and transaction_at < :to",
                vehicleId, rate, start, end);
        BigDecimal maintenance = amount("select coalesce(sum(total_cost), 0) from maintenance_records "
                + "where vehicle_id = :vehicleId and coalesce(completed_at, reported_at) >= :from and coalesce(completed_at, reported_at) < :to",
                vehicleId, rate, start, end);
        BigDecimal expenses = jdbcClient.sql("select coalesce(sum(case when currency = 'USD' then amount * :rate else amount end), 0) "
                        + "from expenses where vehicle_id = :vehicleId and status in ('APPROVED','PAID') and incurred_on between :from and :to")
                .param("vehicleId", vehicleId).param("rate", rate).param("from", startDate).param("to", endDate)
                .query(BigDecimal.class).single();
        BigDecimal fines = amount("select coalesce(sum(case when currency = 'USD' then amount * :rate else amount end), 0) "
                + "from traffic_fines where vehicle_id = :vehicleId and status <> 'WAIVED' and issued_at >= :from and issued_at < :to",
                vehicleId, rate, start, end);
        BigDecimal distance = jdbcClient.sql("select coalesce(sum(distance_km), 0) from trips "
                        + "where vehicle_id = :vehicleId and status = 'COMPLETED' and coalesce(ended_at, scheduled_start_at) >= :from "
                        + "and coalesce(ended_at, scheduled_start_at) < :to")
                .param("vehicleId", vehicleId).param("from", start).param("to", end)
                .query(BigDecimal.class).single();

        BigDecimal total = fuel.add(maintenance).add(expenses).add(fines);
        BigDecimal costPerKm = distance.signum() > 0 ? total.divide(distance, 2, RoundingMode.HALF_UP) : null;
        return new VehicleCostResponse(vehicle.getId(), vehicle.getPlateNumber(), from, to, Currencies.RWF, fuel, maintenance, expenses, fines,
                total, distance, costPerKm);
    }

    private BigDecimal amount(String sql, Long vehicleId, BigDecimal rate, OffsetDateTime from, OffsetDateTime to) {
        return jdbcClient.sql(sql).param("vehicleId", vehicleId).param("rate", rate).param("from", from).param("to", to)
                .query(BigDecimal.class).single();
    }
}
