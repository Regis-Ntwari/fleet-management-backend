package com.limoz.fleet.telematics.movement;

import com.limoz.fleet.telematics.movement.domain.DailyMovementCalculator;
import com.limoz.fleet.telematics.movement.domain.MovementFlag;
import com.limoz.fleet.telematics.movement.domain.MovementResult;
import com.limoz.fleet.telematics.movement.domain.MovementSample;
import com.limoz.fleet.telematics.movement.domain.MovementThresholds;
import com.limoz.fleet.telematics.movement.domain.TripInterval;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DailyMovementCalculatorTest {

    private static final ZoneId KIGALI = ZoneId.of("Africa/Kigali");
    private static final LocalDate DAY = LocalDate.of(2026, 10, 7);
    private static final MovementThresholds THRESHOLDS = new MovementThresholds(LocalTime.of(22, 0), LocalTime.of(5, 0), 6, 500, 80);

    private static Instant at(int hour, int minute) {
        return DAY.atTime(hour, minute).atZone(KIGALI).toInstant();
    }

    private static MovementSample sample(Instant t, double lat, double lon, double speed, Double odometer, Boolean ignition) {
        return new MovementSample(t, BigDecimal.valueOf(lat), BigDecimal.valueOf(lon), BigDecimal.valueOf(speed),
                odometer == null ? null : BigDecimal.valueOf(odometer), ignition);
    }

    @Test
    @DisplayName("distance is the odometer delta when the first and last samples carry an odometer")
    void distanceFromOdometer() {
        List<MovementSample> samples = List.of(
                sample(at(8, 0), -1.95, 30.06, 40, 1000.0, true),
                sample(at(8, 5), -1.96, 30.07, 40, 1020.0, true),
                sample(at(8, 10), -1.97, 30.08, 0, 1045.5, false));
        MovementResult r = DailyMovementCalculator.fromPositions(samples, DAY, KIGALI, THRESHOLDS, true);
        assertThat(r.distanceKm()).isEqualByComparingTo("45.5");
        assertThat(r.moved()).isTrue();
        assertThat(r.startLatitude()).isEqualByComparingTo("-1.95");
        assertThat(r.endLongitude()).isEqualByComparingTo("30.08");
        assertThat(r.flags()).isEmpty();
    }

    @Test
    @DisplayName("without odometer readings the distance is the sum of great-circle distances between samples")
    void distanceFromHaversine() {
        List<MovementSample> samples = List.of(
                sample(at(9, 0), 0.0, 0.0, 60, null, true),
                sample(at(9, 5), 0.5, 0.0, 60, null, true),
                sample(at(9, 10), 1.0, 0.0, 60, null, true));
        MovementResult r = DailyMovementCalculator.fromPositions(samples, DAY, KIGALI, THRESHOLDS, true);
        // one degree of latitude is about 111.2 km
        assertThat(r.distanceKm().doubleValue()).isBetween(111.0, 111.4);
        assertThat(r.drivingMinutes()).isEqualTo(10);
    }

    @Test
    @DisplayName("driving, idle and night minutes are computed from sample intervals with a night window wrapping midnight")
    void drivingIdleAndNightMinutes() {
        List<MovementSample> samples = List.of(
                // early morning: 04:30 -> 05:00 inside the 22:00-05:00 window
                sample(at(4, 30), -1.95, 30.06, 50, null, true),
                sample(at(4, 40), -1.96, 30.06, 50, null, true),
                sample(at(4, 50), -1.97, 30.06, 50, null, true),
                sample(at(5, 0), -1.98, 30.06, 50, null, true),
                // gap of many hours: not counted as driving
                sample(at(21, 50), -1.98, 30.06, 50, null, true),
                sample(at(22, 0), -1.99, 30.06, 50, null, true),
                sample(at(22, 10), -2.00, 30.06, 0, null, true),
                sample(at(22, 20), -2.00, 30.06, 0, null, true),
                sample(at(22, 30), -2.00, 30.06, 0, null, false));
        MovementResult r = DailyMovementCalculator.fromPositions(samples, DAY, KIGALI, THRESHOLDS, true);
        assertThat(r.drivingMinutes()).isEqualTo(50);
        assertThat(r.idleMinutes()).isEqualTo(20);
        assertThat(r.nightDrivingMinutes()).isEqualTo(40);
        assertThat(r.firstMovementAt()).isEqualTo(at(4, 30));
        assertThat(r.lastMovementAt()).isEqualTo(at(22, 20));
        assertThat(r.maxSpeedKph()).isEqualByComparingTo("50.0");
        assertThat(r.flags()).containsExactly(MovementFlag.NIGHT_DRIVING);
    }

    @Test
    @DisplayName("night overlap handles a window that does not wrap midnight")
    void nightWindowWithoutWrap() {
        long seconds = DailyMovementCalculator.nightOverlapSeconds(at(0, 30), at(1, 30), DAY, KIGALI, LocalTime.of(1, 0), LocalTime.of(4, 0));
        assertThat(seconds).isEqualTo(30 * 60);
        assertThat(DailyMovementCalculator.nightOverlapSeconds(at(5, 0), at(6, 0), DAY, KIGALI, LocalTime.of(1, 0), LocalTime.of(4, 0))).isZero();
    }

    @Test
    @DisplayName("excessive driving hours, high distance and over-speeding are flagged against the thresholds")
    void thresholdFlags() {
        List<MovementSample> samples = new ArrayList<>();
        Instant t = at(6, 0);
        double odometer = 0;
        // 6.5 hours of driving sampled every 5 minutes, 600 km in total, one burst at 95 km/h
        for (int i = 0; i <= 78; i++) {
            double speed = i == 10 ? 95 : 60;
            samples.add(sample(t.plusSeconds(i * 300L), -1.95 + i * 0.01, 30.06, speed, odometer, true));
            odometer += 600.0 / 78;
        }
        MovementResult r = DailyMovementCalculator.fromPositions(samples, DAY, KIGALI, THRESHOLDS, true);
        assertThat(r.drivingMinutes()).isEqualTo(390);
        assertThat(r.distanceKm()).isEqualByComparingTo("600.0");
        assertThat(r.maxSpeedKph()).isEqualByComparingTo("95.0");
        assertThat(r.flags()).containsExactlyInAnyOrder(MovementFlag.EXCESSIVE_DRIVING_HOURS, MovementFlag.HIGH_DAILY_DISTANCE, MovementFlag.OVER_SPEEDING);
        assertThat(r.gpsIssue()).isFalse();
    }

    @Test
    @DisplayName("a parked vehicle reporting positions is NOT_MOVED")
    void parkedVehicle() {
        List<MovementSample> samples = List.of(
                sample(at(10, 0), -1.95, 30.06, 0, 500.0, false),
                sample(at(10, 5), -1.95, 30.06, 0, 500.0, false),
                sample(at(10, 10), -1.95001, 30.06001, 1, 500.0, false));
        MovementResult r = DailyMovementCalculator.fromPositions(samples, DAY, KIGALI, THRESHOLDS, true);
        assertThat(r.moved()).isFalse();
        assertThat(r.distanceKm()).isEqualByComparingTo("0.0");
        assertThat(r.flags()).containsExactly(MovementFlag.NOT_MOVED);
        assertThat(r.firstMovementAt()).isNull();
    }

    @Test
    @DisplayName("no samples at all: NOT_MOVED, plus GPS_OFFLINE when the vehicle has an active device")
    void noSamples() {
        MovementResult withDevice = DailyMovementCalculator.fromPositions(List.of(), DAY, KIGALI, THRESHOLDS, true);
        assertThat(withDevice.flags()).containsExactlyInAnyOrder(MovementFlag.NOT_MOVED, MovementFlag.GPS_OFFLINE);
        assertThat(withDevice.gpsIssue()).isTrue();
        assertThat(withDevice.moved()).isFalse();

        MovementResult withoutDevice = DailyMovementCalculator.fromPositions(null, DAY, KIGALI, THRESHOLDS, false);
        assertThat(withoutDevice.flags()).containsExactly(MovementFlag.NOT_MOVED);
        assertThat(withoutDevice.gpsIssue()).isFalse();
    }

    @Test
    @DisplayName("trip records give distance, driving time, trip count and night overlap")
    void fromTrips() {
        List<TripInterval> trips = List.of(
                new TripInterval(1L, at(21, 0), at(23, 0), new BigDecimal("120.0"), 120, new BigDecimal("75.0")),
                new TripInterval(2L, at(8, 0), at(9, 0), new BigDecimal("30.5"), 60, null));
        MovementResult r = DailyMovementCalculator.fromTrips(trips, DAY, KIGALI, THRESHOLDS, false);
        assertThat(r.tripsCount()).isEqualTo(2);
        assertThat(r.distanceKm()).isEqualByComparingTo("150.5");
        assertThat(r.drivingMinutes()).isEqualTo(180);
        assertThat(r.nightDrivingMinutes()).isEqualTo(60);
        assertThat(r.firstMovementAt()).isEqualTo(at(8, 0));
        assertThat(r.lastMovementAt()).isEqualTo(at(23, 0));
        assertThat(r.moved()).isTrue();
        assertThat(r.flags()).containsExactly(MovementFlag.NIGHT_DRIVING);

        MovementResult withOfflineDevice = DailyMovementCalculator.fromTrips(trips, DAY, KIGALI, THRESHOLDS, true);
        assertThat(withOfflineDevice.flags()).containsExactlyInAnyOrder(MovementFlag.NIGHT_DRIVING, MovementFlag.GPS_OFFLINE);
        assertThat(withOfflineDevice.gpsIssue()).isTrue();
    }
}
