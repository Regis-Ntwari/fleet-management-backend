package com.limoz.fleet.dashboard;

import com.limoz.fleet.common.util.DateRanges;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

/**
 * Aggregate SQL for the dashboard. Everything is computed in PostgreSQL over indexed columns; the service never
 * loads raw operational rows. Business dates are converted to UTC instant ranges with the operational zone.
 */
@Repository
@RequiredArgsConstructor
public class DashboardQueries {

    private final JdbcClient jdbc;
    private final ZoneId zone;

    public record LabelCount(String label, long count, BigDecimal value) {}
    public record DayValue(LocalDate day, long count, BigDecimal value) {}

    public long count(String sql, Map<String, ?> params) {
        return jdbc.sql(sql).params(params).query(Long.class).optional().orElse(0L);
    }

    public BigDecimal sum(String sql, Map<String, ?> params) {
        BigDecimal v = jdbc.sql(sql).params(params).query(BigDecimal.class).optional().orElse(BigDecimal.ZERO);
        return v == null ? BigDecimal.ZERO : v;
    }

    public List<LabelCount> labelCounts(String sql, Map<String, ?> params) {
        return jdbc.sql(sql).params(params).query((rs, i) -> new LabelCount(rs.getString(1), rs.getLong(2),
                rs.getMetaData().getColumnCount() > 2 ? nz(rs.getBigDecimal(3)) : null)).list();
    }

    public List<DayValue> dayValues(String sql, Map<String, ?> params) {
        return jdbc.sql(sql).params(params).query((rs, i) -> new DayValue(rs.getDate(1).toLocalDate(), rs.getLong(2), nz(rs.getBigDecimal(3)))).list();
    }

    public <T> List<T> list(String sql, Map<String, ?> params, org.springframework.jdbc.core.RowMapper<T> mapper) {
        return jdbc.sql(sql).params(params).query(mapper).list();
    }

    public Timestamp startOf(LocalDate date) {
        return Timestamp.from(DateRanges.forDate(date, zone).from());
    }

    public Timestamp endOf(LocalDate date) {
        return Timestamp.from(DateRanges.forDate(date, zone).to());
    }

    public Timestamp ts(Instant instant) {
        return Timestamp.from(instant);
    }

    /** SQL expression converting a TIMESTAMPTZ column to the operational-zone business date. */
    public String localDate(String column) {
        return "(" + column + " AT TIME ZONE '" + zone.getId() + "')::date";
    }

    public static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
