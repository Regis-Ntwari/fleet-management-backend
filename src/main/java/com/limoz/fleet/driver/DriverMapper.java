package com.limoz.fleet.driver;

import com.limoz.fleet.driver.dto.DriverResponse;
import com.limoz.fleet.driver.dto.DriverSummary;
import com.limoz.fleet.settings.SettingKeys;
import com.limoz.fleet.settings.SettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;

@Component
@RequiredArgsConstructor
public class DriverMapper {

    private final SettingsService settingsService;
    private final Clock clock;

    public DriverResponse toResponse(Driver d) {
        return new DriverResponse(d.getId(), d.getDriverCode(), d.getFirstName(), d.getLastName(), d.getFullName(), d.getPhone(),
                d.getEmail(), d.getNationalId(), d.getLicenseNumber(), d.getLicenseCategory(), d.getLicenseIssueDate(),
                d.getLicenseExpiryDate(), licenseStatus(d.getLicenseExpiryDate()), d.getEmploymentStatus(), d.getStatus(),
                d.getCurrentVehicle() == null ? null : d.getCurrentVehicle().getId(),
                d.getCurrentVehicle() == null ? null : d.getCurrentVehicle().getPlateNumber(),
                d.getBasedIn(), d.getEmergencyContactName(), d.getEmergencyContactPhone(), d.getJoiningDate(), d.getDateOfBirth(),
                d.getPhotoAttachmentId(), d.getNotes(), d.isArchived(), d.getCreatedAt(), d.getUpdatedAt());
    }

    public DriverSummary toSummary(Driver d) {
        return d == null ? null : new DriverSummary(d.getId(), d.getDriverCode(), d.getFullName(), d.getPhone(), d.getLicenseNumber(), d.getStatus());
    }

    public String licenseStatus(LocalDate expiry) {
        if (expiry == null) return "UNKNOWN";
        LocalDate today = LocalDate.now(clock);
        if (expiry.isBefore(today)) return "EXPIRED";
        int warn = settingsService.getInt(SettingKeys.LICENSE_EXPIRY_WARNING_DAYS);
        if (!expiry.isAfter(today.plusDays(warn))) return "EXPIRING_SOON";
        return "VALID";
    }
}
