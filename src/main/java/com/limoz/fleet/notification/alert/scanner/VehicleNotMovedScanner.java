package com.limoz.fleet.notification.alert.scanner;

import com.limoz.fleet.notification.NotificationSeverity;
import com.limoz.fleet.notification.alert.AlertCandidate;
import com.limoz.fleet.notification.alert.AlertScanner;
import com.limoz.fleet.notification.alert.AlertType;
import com.limoz.fleet.settings.SettingKeys;
import com.limoz.fleet.settings.SettingsService;
import com.limoz.fleet.telematics.movement.DailyMovementSummaryRepository;
import com.limoz.fleet.vehicle.Vehicle;
import com.limoz.fleet.vehicle.VehicleRepository;
import com.limoz.fleet.vehicle.VehicleStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Idle fleet: an AVAILABLE or ASSIGNED vehicle with no day marked as moved in the last
 * {@code movement.idle_days_threshold} days (missing summaries count as not moved) -> WARNING.
 * Vehicles registered inside the window are skipped so a newly added vehicle is not flagged on day one.
 */
@Component
@RequiredArgsConstructor
public class VehicleNotMovedScanner implements AlertScanner {

    private static final List<VehicleStatus> WATCHED = List.of(VehicleStatus.AVAILABLE, VehicleStatus.ASSIGNED);

    private final VehicleRepository vehicleRepository;
    private final DailyMovementSummaryRepository summaryRepository;
    private final SettingsService settingsService;
    private final Clock clock;
    private final ZoneId operationalZone;

    @Override
    public List<AlertCandidate> scan() {
        int days = Math.max(1, settingsService.getInt(SettingKeys.IDLE_DAYS_THRESHOLD));
        LocalDate yesterday = LocalDate.now(clock).minusDays(1);
        LocalDate windowStart = yesterday.minusDays(days - 1L);
        Instant windowStartInstant = windowStart.atStartOfDay(operationalZone).toInstant();
        Set<Long> moved = summaryRepository.vehicleIdsMovedBetween(windowStart, yesterday);
        List<AlertCandidate> out = new ArrayList<>();
        for (Vehicle v : vehicleRepository.findByArchivedFalseAndOperationalStatusInOrderByPlateNumberAsc(WATCHED)) {
            if (moved.contains(v.getId()) || v.getCreatedAt() == null || v.getCreatedAt().isAfter(windowStartInstant)) {
                continue;
            }
            out.add(new AlertCandidate(AlertType.VEHICLE_NOT_MOVED, NotificationSeverity.WARNING,
                    "Vehicle idle: " + v.getPlateNumber(),
                    v.getPlateNumber() + " (" + v.getOperationalStatus() + ") has not moved for " + days + " day(s) since " + windowStart,
                    "Vehicle", v.getId(), v.getPlateNumber(), "/vehicles/" + v.getId() + "/movement",
                    AlertCandidate.key(AlertType.VEHICLE_NOT_MOVED, "Vehicle", v.getId(), null)));
        }
        return out;
    }
}
