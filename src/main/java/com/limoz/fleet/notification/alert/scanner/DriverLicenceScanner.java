package com.limoz.fleet.notification.alert.scanner;

import com.limoz.fleet.driver.Driver;
import com.limoz.fleet.driver.DriverRepository;
import com.limoz.fleet.notification.NotificationSeverity;
import com.limoz.fleet.notification.alert.AlertCandidate;
import com.limoz.fleet.notification.alert.AlertScanner;
import com.limoz.fleet.notification.alert.AlertType;
import com.limoz.fleet.settings.SettingKeys;
import com.limoz.fleet.settings.SettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Driver licences: expired -> CRITICAL (the driver cannot be dispatched), expiring within
 * {@code documents.license_expiry_warning_days} -> WARNING.
 */
@Component
@RequiredArgsConstructor
public class DriverLicenceScanner implements AlertScanner {

    private final DriverRepository driverRepository;
    private final SettingsService settingsService;
    private final Clock clock;

    @Override
    public List<AlertCandidate> scan() {
        LocalDate today = LocalDate.now(clock);
        int warningDays = settingsService.getInt(SettingKeys.LICENSE_EXPIRY_WARNING_DAYS);
        List<AlertCandidate> out = new ArrayList<>();
        for (Driver d : driverRepository.findByArchivedFalseAndLicenseExpiryDateBefore(today)) {
            long daysAgo = ChronoUnit.DAYS.between(d.getLicenseExpiryDate(), today);
            out.add(candidate(d, AlertType.LICENSE_EXPIRED, NotificationSeverity.CRITICAL,
                    "Driving licence expired: " + d.getFullName(),
                    "Licence " + d.getLicenseNumber() + " of " + d.getFullName() + " expired on " + d.getLicenseExpiryDate()
                            + " (" + daysAgo + " day(s) ago)"));
        }
        for (Driver d : driverRepository.findByArchivedFalseAndLicenseExpiryDateBetween(today, today.plusDays(warningDays))) {
            long daysLeft = ChronoUnit.DAYS.between(today, d.getLicenseExpiryDate());
            out.add(candidate(d, AlertType.LICENSE_EXPIRING, NotificationSeverity.WARNING,
                    "Driving licence expiring: " + d.getFullName(),
                    "Licence " + d.getLicenseNumber() + " of " + d.getFullName() + " expires on " + d.getLicenseExpiryDate()
                            + " (in " + daysLeft + " day(s))"));
        }
        return out;
    }

    private static AlertCandidate candidate(Driver d, AlertType type, NotificationSeverity severity, String title, String message) {
        return new AlertCandidate(type, severity, title, message, "Driver", d.getId(), d.getFullName(), "/drivers/" + d.getId(),
                AlertCandidate.key(type, "Driver", d.getId(), null));
    }
}
