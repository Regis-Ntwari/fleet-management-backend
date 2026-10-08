package com.limoz.fleet.telematics.movement;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-only access to the {@code trips} table for movement analysis. The trip aggregate belongs to the dispatch
 * module; this query deliberately reads the few columns it needs natively instead of mapping the entity.
 */
@Repository
@RequiredArgsConstructor
public class TripMovementQuery {

    private static final String SQL = """
            select vehicle_id, id, started_at, ended_at, distance_km, duration_minutes, max_speed_kph
            from trips
            where started_at >= :from and started_at < :to and status <> 'CANCELLED'
            order by vehicle_id, started_at
            """;

    private final JdbcClient jdbc;

    /** Trips started in [from, to), grouped by vehicle id. */
    public Map<Long, List<TripInterval>> tripsStartedBetween(Instant from, Instant to) {
        return jdbc.sql(SQL)
                .param("from", OffsetDateTime.ofInstant(from, ZoneOffset.UTC))
                .param("to", OffsetDateTime.ofInstant(to, ZoneOffset.UTC))
                .query((ResultSet rs) -> {
                    Map<Long, List<TripInterval>> result = new LinkedHashMap<>();
                    while (rs.next()) {
                        result.computeIfAbsent(rs.getLong("vehicle_id"), k -> new ArrayList<>()).add(map(rs));
                    }
                    return result;
                });
    }

    private static TripInterval map(ResultSet rs) throws SQLException {
        BigDecimal distance = rs.getBigDecimal("distance_km");
        int duration = rs.getInt("duration_minutes");
        Integer durationMinutes = rs.wasNull() ? null : duration;
        return new TripInterval(rs.getLong("id"), instant(rs, "started_at"), instant(rs, "ended_at"), distance, durationMinutes,
                rs.getBigDecimal("max_speed_kph"));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }
}
