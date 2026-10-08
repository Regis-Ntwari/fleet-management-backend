package com.limoz.fleet.telematics.movement;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

/**
 * Pure, side-effect free daily movement analysis for one vehicle.
 * <p>
 * Rules (positions):
 * <ul>
 *   <li>A sample is <em>moving</em> when its speed is above {@value #MOVING_SPEED_KPH} km/h. The time between a moving
 *       sample and the next sample counts as driving; between an ignition-on, non-moving sample and the next sample as idle.
 *       Gaps longer than {@link #MAX_INTERVAL} (signal loss, device off) are not counted at all.</li>
 *   <li>Distance is the odometer delta when both the first and the last sample carry an odometer reading, otherwise the sum
 *       of great-circle distances between consecutive samples.</li>
 *   <li>First / last movement are the first and last samples that are moving or have the ignition on.</li>
 *   <li>Night driving is the part of the driving intervals that overlaps the night window, which may wrap midnight.</li>
 *   <li>The vehicle has moved when it covered at least {@value #MIN_MOVED_DISTANCE_KM} km or any sample was moving.</li>
 * </ul>
 * Rules (trips, used when no positions exist): distance = sum of trip distances, driving = sum of trip durations,
 * first / last movement = earliest start / latest end, night driving = overlap of the trip intervals with the night window.
 */
public final class DailyMovementCalculator {

    public static final double MOVING_SPEED_KPH = 2.0;
    public static final double MIN_MOVED_DISTANCE_KM = 0.5;
    public static final Duration MAX_INTERVAL = Duration.ofMinutes(10);
    private static final double EARTH_RADIUS_KM = 6371.0088;

    private DailyMovementCalculator() {}

    public static MovementResult fromPositions(List<MovementSample> samples, LocalDate date, ZoneId zone,
                                               MovementThresholds thresholds, boolean activeDevice) {
        if (samples == null || samples.isEmpty()) {
            return empty(activeDevice);
        }
        List<MovementSample> ordered = new ArrayList<>(samples);
        ordered.sort(Comparator.comparing(MovementSample::recordedAt));
        MovementSample first = ordered.getFirst();
        MovementSample last = ordered.getLast();

        long drivingSeconds = 0;
        long idleSeconds = 0;
        long nightSeconds = 0;
        double haversineKm = 0;
        double maxSpeed = 0;
        boolean anyMoving = false;
        Instant firstMovement = null;
        Instant lastMovement = null;

        for (int i = 0; i < ordered.size(); i++) {
            MovementSample s = ordered.get(i);
            double speed = s.speed();
            maxSpeed = Math.max(maxSpeed, speed);
            boolean moving = speed > MOVING_SPEED_KPH;
            anyMoving |= moving;
            if (moving || s.ignition()) {
                if (firstMovement == null) firstMovement = s.recordedAt();
                lastMovement = s.recordedAt();
            }
            if (i + 1 < ordered.size()) {
                MovementSample next = ordered.get(i + 1);
                haversineKm += haversineKm(s, next);
                Duration gap = Duration.between(s.recordedAt(), next.recordedAt());
                if (gap.isNegative() || gap.compareTo(MAX_INTERVAL) > 0) {
                    continue;
                }
                if (moving) {
                    drivingSeconds += gap.toSeconds();
                    nightSeconds += nightOverlapSeconds(s.recordedAt(), next.recordedAt(), date, zone, thresholds.nightStart(), thresholds.nightEnd());
                } else if (s.ignition()) {
                    idleSeconds += gap.toSeconds();
                }
            }
        }

        BigDecimal distance;
        if (first.odometerKm() != null && last.odometerKm() != null && last.odometerKm().compareTo(first.odometerKm()) >= 0) {
            distance = last.odometerKm().subtract(first.odometerKm());
        } else {
            distance = BigDecimal.valueOf(haversineKm);
        }
        distance = distance.setScale(1, RoundingMode.HALF_UP);
        boolean moved = distance.doubleValue() >= MIN_MOVED_DISTANCE_KM || anyMoving;

        int drivingMinutes = minutes(drivingSeconds);
        int idleMinutes = minutes(idleSeconds);
        int nightMinutes = minutes(nightSeconds);
        BigDecimal maxSpeedKph = BigDecimal.valueOf(maxSpeed).setScale(1, RoundingMode.HALF_UP);
        List<MovementFlag> flags = flags(moved, distance, drivingMinutes, nightMinutes, maxSpeedKph, thresholds, false);
        return new MovementResult(distance, moved, firstMovement, lastMovement, drivingMinutes, idleMinutes, nightMinutes, maxSpeedKph, 0,
                first.latitude(), first.longitude(), last.latitude(), last.longitude(), false, flags);
    }

    public static MovementResult fromTrips(List<TripInterval> trips, LocalDate date, ZoneId zone,
                                           MovementThresholds thresholds, boolean activeDevice) {
        if (trips == null || trips.isEmpty()) {
            return empty(activeDevice);
        }
        BigDecimal distance = BigDecimal.ZERO;
        long drivingMinutesTotal = 0;
        long nightSeconds = 0;
        double maxSpeed = 0;
        Instant first = null;
        Instant last = null;
        for (TripInterval t : trips) {
            if (t.distanceKm() != null) distance = distance.add(t.distanceKm());
            if (t.durationMinutes() != null) drivingMinutesTotal += t.durationMinutes();
            if (t.maxSpeedKph() != null) maxSpeed = Math.max(maxSpeed, t.maxSpeedKph().doubleValue());
            if (t.startedAt() != null && (first == null || t.startedAt().isBefore(first))) first = t.startedAt();
            Instant end = t.endedAt() != null ? t.endedAt() : t.startedAt();
            if (end != null && (last == null || end.isAfter(last))) last = end;
            if (t.startedAt() != null && t.endedAt() != null) {
                nightSeconds += nightOverlapSeconds(t.startedAt(), t.endedAt(), date, zone, thresholds.nightStart(), thresholds.nightEnd());
            }
        }
        distance = distance.setScale(1, RoundingMode.HALF_UP);
        int drivingMinutes = (int) Math.min(Integer.MAX_VALUE, drivingMinutesTotal);
        int nightMinutes = minutes(nightSeconds);
        BigDecimal maxSpeedKph = BigDecimal.valueOf(maxSpeed).setScale(1, RoundingMode.HALF_UP);
        List<MovementFlag> flags = flags(true, distance, drivingMinutes, nightMinutes, maxSpeedKph, thresholds, activeDevice);
        return new MovementResult(distance, true, first, last, drivingMinutes, 0, nightMinutes, maxSpeedKph, trips.size(),
                null, null, null, null, activeDevice, flags);
    }

    private static MovementResult empty(boolean activeDevice) {
        List<MovementFlag> flags = new ArrayList<>();
        flags.add(MovementFlag.NOT_MOVED);
        if (activeDevice) flags.add(MovementFlag.GPS_OFFLINE);
        return new MovementResult(BigDecimal.ZERO.setScale(1), false, null, null, 0, 0, 0, BigDecimal.ZERO.setScale(1), 0,
                null, null, null, null, activeDevice, List.copyOf(flags));
    }

    private static List<MovementFlag> flags(boolean moved, BigDecimal distance, int drivingMinutes, int nightMinutes,
                                            BigDecimal maxSpeed, MovementThresholds t, boolean gpsOffline) {
        EnumSet<MovementFlag> flags = EnumSet.noneOf(MovementFlag.class);
        if (!moved) flags.add(MovementFlag.NOT_MOVED);
        if (drivingMinutes > t.excessiveDrivingHours() * 60L) flags.add(MovementFlag.EXCESSIVE_DRIVING_HOURS);
        if (distance.compareTo(BigDecimal.valueOf(t.highDailyDistanceKm())) > 0) flags.add(MovementFlag.HIGH_DAILY_DISTANCE);
        if (nightMinutes > 0) flags.add(MovementFlag.NIGHT_DRIVING);
        if (maxSpeed.compareTo(BigDecimal.valueOf(t.speedLimitKph())) > 0) flags.add(MovementFlag.OVER_SPEEDING);
        if (gpsOffline) flags.add(MovementFlag.GPS_OFFLINE);
        return List.copyOf(flags);
    }

    /**
     * Seconds of [from, to) that fall inside the night window of the given operational day. A window whose start
     * is after its end (e.g. 22:00-05:00) wraps midnight and covers both the early morning and the late evening of the day.
     */
    static long nightOverlapSeconds(Instant from, Instant to, LocalDate date, ZoneId zone, LocalTime nightStart, LocalTime nightEnd) {
        if (nightStart == null || nightEnd == null || nightStart.equals(nightEnd) || !from.isBefore(to)) {
            return 0;
        }
        Instant dayStart = date.atStartOfDay(zone).toInstant();
        Instant dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant();
        Instant startToday = date.atTime(nightStart).atZone(zone).toInstant();
        Instant endToday = date.atTime(nightEnd).atZone(zone).toInstant();
        long seconds = 0;
        if (nightStart.isBefore(nightEnd)) {
            seconds += overlap(from, to, startToday, endToday);
        } else {
            seconds += overlap(from, to, dayStart, endToday);
            seconds += overlap(from, to, startToday, dayEnd);
        }
        return seconds;
    }

    private static long overlap(Instant from, Instant to, Instant windowStart, Instant windowEnd) {
        Instant start = from.isAfter(windowStart) ? from : windowStart;
        Instant end = to.isBefore(windowEnd) ? to : windowEnd;
        return start.isBefore(end) ? Duration.between(start, end).toSeconds() : 0;
    }

    static double haversineKm(MovementSample a, MovementSample b) {
        if (a.latitude() == null || a.longitude() == null || b.latitude() == null || b.longitude() == null) {
            return 0;
        }
        double lat1 = Math.toRadians(a.latitude().doubleValue());
        double lat2 = Math.toRadians(b.latitude().doubleValue());
        double dLat = lat2 - lat1;
        double dLon = Math.toRadians(b.longitude().doubleValue() - a.longitude().doubleValue());
        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2) + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * EARTH_RADIUS_KM * Math.asin(Math.min(1, Math.sqrt(h)));
    }

    private static int minutes(long seconds) {
        return (int) Math.round(seconds / 60.0);
    }
}
