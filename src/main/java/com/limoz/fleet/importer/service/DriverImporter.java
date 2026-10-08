package com.limoz.fleet.importer.service;

import com.limoz.fleet.importer.domain.ImportSupport;
import com.limoz.fleet.importer.repository.TabularFileReader;

import com.limoz.fleet.common.exception.DuplicateResourceException;
import com.limoz.fleet.driver.repository.DriverRepository;
import com.limoz.fleet.driver.service.DriverService;
import com.limoz.fleet.driver.domain.EmploymentStatus;
import com.limoz.fleet.driver.dto.DriverRequest;
import com.limoz.fleet.importer.dto.ImportResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Driver list import. Columns: first_name*, last_name* (or full_name*), license_number*, phone, email, national_id,
 * license_category, license_issue_date, license_expiry_date, employment_status, based_in, emergency_contact_name,
 * emergency_contact_phone, joining_date, date_of_birth, notes.
 */
@Component
@RequiredArgsConstructor
public class DriverImporter {

    public static final List<String> COLUMNS = List.of("first_name", "last_name", "full_name", "license_number", "phone", "email",
            "national_id", "license_category", "license_issue_date", "license_expiry_date", "employment_status", "based_in",
            "emergency_contact_name", "emergency_contact_phone", "joining_date", "date_of_birth", "notes");

    private final TabularFileReader reader;
    private final DriverService driverService;
    private final DriverRepository driverRepository;

    public ImportResult importFile(MultipartFile file, boolean updateExisting) {
        List<TabularFileReader.TabularRow> rows = reader.read(file);
        List<ImportResult.RowError> errors = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int imported = 0, updated = 0, duplicates = 0;
        for (TabularFileReader.TabularRow row : rows) {
            String licence = row.get("license_number") != null ? row.get("license_number") : row.get("licence_number");
            try {
                DriverRequest request = toRequest(row, licence);
                if (!seen.add(request.licenseNumber().toUpperCase(Locale.ROOT))) {
                    duplicates++;
                    errors.add(new ImportResult.RowError(row.rowNumber(), licence, "Duplicate licence number within the file"));
                    continue;
                }
                var existing = driverRepository.findByLicenseNumberIgnoreCase(request.licenseNumber());
                if (existing.isPresent()) {
                    if (updateExisting) {
                        driverService.update(existing.get().getId(), request);
                        updated++;
                    } else {
                        duplicates++;
                        errors.add(new ImportResult.RowError(row.rowNumber(), licence, "Driver already exists (licence " + request.licenseNumber() + ")"));
                    }
                    continue;
                }
                driverService.create(request);
                imported++;
            } catch (DuplicateResourceException e) {
                duplicates++;
                errors.add(new ImportResult.RowError(row.rowNumber(), licence, e.getMessage()));
            } catch (RuntimeException e) {
                errors.add(new ImportResult.RowError(row.rowNumber(), licence, e.getMessage()));
            }
        }
        return new ImportResult("DRIVERS", file.getOriginalFilename(), rows.size(), imported, updated,
                rows.size() - imported - updated, duplicates, errors);
    }


    private DriverRequest toRequest(TabularFileReader.TabularRow row, String licence) {
        String first = row.get("first_name");
        String last = row.get("last_name");
        if ((first == null || last == null) && row.get("full_name") != null) {
            String[] parts = row.get("full_name").trim().split("\\s+", 2);
            first = first == null ? parts[0] : first;
            last = last == null ? (parts.length > 1 ? parts[1] : parts[0]) : last;
        }
        return new DriverRequest(
                ImportSupport.required(first, "first_name"),
                ImportSupport.required(last, "last_name"),
                row.get("phone"),
                row.get("email"),
                row.get("national_id"),
                ImportSupport.required(licence, "license_number"),
                row.get("license_category"),
                ImportSupport.parseDate(row.get("license_issue_date"), "license_issue_date"),
                ImportSupport.parseDate(row.get("license_expiry_date"), "license_expiry_date"),
                ImportSupport.parseEnum(row.get("employment_status"), EmploymentStatus.class, "employment_status", EmploymentStatus.FULL_TIME),
                row.get("based_in"),
                row.get("emergency_contact_name"),
                row.get("emergency_contact_phone"),
                ImportSupport.parseDate(row.get("joining_date"), "joining_date"),
                ImportSupport.parseDate(row.get("date_of_birth"), "date_of_birth"),
                null,
                row.get("notes"));
    }
}
