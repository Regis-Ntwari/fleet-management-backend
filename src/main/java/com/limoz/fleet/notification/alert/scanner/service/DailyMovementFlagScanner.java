package com.limoz.fleet.notification.alert.scanner.service;

import com.limoz.fleet.notification.domain.NotificationSeverity;
import com.limoz.fleet.notification.alert.domain.AlertCandidate;
import com.limoz.fleet.notification.alert.service.AlertScanner;
import com.limoz.fleet.notification.alert.domain.AlertType;
import com.limoz.fleet.telematics.movement.domain.DailyMovementSummary;
import com.limoz.fleet.telematics.movement.repository.DailyMovementSummaryRepository;
import com.limoz.fleet.telematics.movement.domain.MovementFlag;
import com.limoz.fleet.vehicle.domain.Vehicle;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Yesterday's movement summaries flagged for excessive driving, high distance, night driving or over-speeding -> WARNING. */
@Component
@RequiredArgsConstructor
public class DailyMovementFlagScanner implements AlertScanner {

    private static final Map<MovementFlag, AlertType> TYPES = Map.of(
            MovementFlag.EXCESSIVE_DRIVING_HOURS, AlertType.EXCESSIVE_DRIVING_HOURS,
            MovementFlag.HIGH_DAILY_DISTANCE, AlertType.HIGH_DAILY_DISTANCE,
            MovementFlag.NIGHT_DRIVING, AlertType.NIGHT_DRIVING,
            MovementFlag.OVER_SPEEDING, AlertType.OVER_SPEEDING);

    private final DailyMovementSummaryRepository summaryRepository;
    private final Clock clock;

    @Override
    public List<AlertCandidate> scan() {
        LocalDate yesterday = LocalDate.now(clock).minusDays(1);
        List<AlertCandidate> out = new ArrayList<>();
        for (DailyMovementSummary s : summaryRepository.findByDate(yesterday)) {
            for (MovementFlag flag : s.movementFlags()) {
                AlertType type = TYPES.get(flag);
                if (type != null) {
                    out.add(candidate(s, type));
                }
            }
        }
        return out;
    }

    private static AlertCandidate candidate(DailyMovementSummary s, AlertType type) {
        Vehicle v = s.getVehicle();
        String detail = switch (type) {
            case EXCESSIVE_DRIVING_HOURS -> "driven " + s.getDrivingMinutes() / 60 + "h" + String.format("%02d", s.getDrivingMinutes() % 60);
            case HIGH_DAILY_DISTANCE -> "travelled " + s.getDistanceKm() + " km";
            case NIGHT_DRIVING -> s.getNightDrivingMinutes() + " minute(s) of night driving";
            case OVER_SPEEDING -> "maximum speed " + s.getMaxSpeedKph() + " km/h";
            default -> type.name();
        };
        String label = type.name().toLowerCase().replace('_', ' ');
        return new AlertCandidate(type, NotificationSeverity.WARNING,
                Character.toUpperCase(label.charAt(0)) + label.substring(1) + ": " + v.getPlateNumber(),
                v.getPlateNumber() + " on " + s.getSummaryDate() + ": " + detail,
                "DailyMovementSummary", s.getId(), v.getPlateNumber() + " " + s.getSummaryDate(),
                "/vehicles/" + v.getId() + "/movement?from=" + s.getSummaryDate() + "&to=" + s.getSummaryDate(),
                AlertCandidate.key(type, "Vehicle", v.getId(), s.getSummaryDate().toString()));
    }
}
