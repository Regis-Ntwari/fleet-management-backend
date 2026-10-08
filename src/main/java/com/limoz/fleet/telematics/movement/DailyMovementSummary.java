package com.limoz.fleet.telematics.movement;

import com.limoz.fleet.vehicle.Vehicle;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * One row per vehicle per operational day (Africa/Kigali), recomputed by the nightly job or on demand.
 * The table has no auditing columns, so this entity does not extend {@code BaseEntity}.
 */
@Entity
@Table(name = "daily_movement_summaries")
@Getter
@Setter
@NoArgsConstructor
public class DailyMovementSummary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    @Column(name = "summary_date", nullable = false)
    private LocalDate summaryDate;

    @Column(name = "distance_km", nullable = false, precision = 10, scale = 1)
    private BigDecimal distanceKm = BigDecimal.ZERO;

    @Column(nullable = false)
    private boolean moved;

    @Column(name = "first_movement_at")
    private Instant firstMovementAt;

    @Column(name = "last_movement_at")
    private Instant lastMovementAt;

    @Column(name = "driving_minutes", nullable = false)
    private int drivingMinutes;

    @Column(name = "idle_minutes", nullable = false)
    private int idleMinutes;

    @Column(name = "night_driving_minutes", nullable = false)
    private int nightDrivingMinutes;

    @Column(name = "max_speed_kph", nullable = false, precision = 6, scale = 1)
    private BigDecimal maxSpeedKph = BigDecimal.ZERO;

    @Column(name = "trips_count", nullable = false)
    private int tripsCount;

    @Column(name = "start_latitude", precision = 9, scale = 6)
    private BigDecimal startLatitude;

    @Column(name = "start_longitude", precision = 9, scale = 6)
    private BigDecimal startLongitude;

    @Column(name = "end_latitude", precision = 9, scale = 6)
    private BigDecimal endLatitude;

    @Column(name = "end_longitude", precision = 9, scale = 6)
    private BigDecimal endLongitude;

    @Column(name = "start_location", length = 255)
    private String startLocation;

    @Column(name = "end_location", length = 255)
    private String endLocation;

    @Column(name = "gps_issue", nullable = false)
    private boolean gpsIssue;

    /** Names of {@link MovementFlag} values, stored as a JSONB array. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<String> flags = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "data_source", nullable = false, length = 20)
    private MovementDataSource dataSource = MovementDataSource.NONE;

    @Column(name = "computed_at", nullable = false)
    private Instant computedAt;

    public List<MovementFlag> movementFlags() {
        return flags.stream().map(MovementFlag::valueOf).toList();
    }
}
