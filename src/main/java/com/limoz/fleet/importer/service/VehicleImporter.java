package com.limoz.fleet.importer.service;

import com.limoz.fleet.importer.domain.ImportSupport;
import com.limoz.fleet.importer.repository.TabularFileReader;

import com.limoz.fleet.common.exception.DuplicateResourceException;
import com.limoz.fleet.importer.dto.ImportResult;
import com.limoz.fleet.vehicle.domain.FuelType;
import com.limoz.fleet.vehicle.domain.OwnershipType;
import com.limoz.fleet.vehicle.domain.Transmission;
import com.limoz.fleet.vehicle.domain.Vehicle;
import com.limoz.fleet.vehicle.domain.VehicleCategory;
import com.limoz.fleet.vehicle.repository.VehicleCategoryRepository;
import com.limoz.fleet.vehicle.repository.VehicleRepository;
import com.limoz.fleet.vehicle.service.VehicleService;
import com.limoz.fleet.vehicle.dto.VehicleRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Vehicle list import. Columns (header names are case/space-insensitive):
 * plate_number*, make*, model*, category* (code or name), year, fleet_number, body_type, fuel_type, transmission,
 * engine_number, chassis_number, color, odometer_km, seating_capacity, purchase_date, acquisition_cost,
 * ownership_type, owner_name, insurance_provider, insurance_policy_number, insurance_expiry_date, day_rate, department, notes.
 * Existing plates are skipped as duplicates (never overwritten) unless updateExisting = true.
 * Each row is saved in its own transaction (the services are transactional and no outer transaction exists),
 * so one bad row never rolls back valid ones.
 */
@Component
@RequiredArgsConstructor
public class VehicleImporter {

    public static final List<String> COLUMNS = List.of("plate_number", "make", "model", "category", "year", "fleet_number",
            "body_type", "fuel_type", "transmission", "engine_number", "chassis_number", "color", "odometer_km", "seating_capacity",
            "purchase_date", "acquisition_cost", "ownership_type", "owner_name", "insurance_provider", "insurance_policy_number",
            "insurance_expiry_date", "day_rate", "department", "notes");

    private final TabularFileReader reader;
    private final VehicleService vehicleService;
    private final VehicleRepository vehicleRepository;
    private final VehicleCategoryRepository categoryRepository;

    public ImportResult importFile(MultipartFile file, boolean updateExisting) {
        List<TabularFileReader.TabularRow> rows = reader.read(file);
        List<ImportResult.RowError> errors = new ArrayList<>();
        Set<String> seenPlates = new HashSet<>();
        int imported = 0, updated = 0, duplicates = 0;
        for (TabularFileReader.TabularRow row : rows) {
            String plate = row.get("plate_number");
            try {
                VehicleRequest request = toRequest(row);
                String key = Vehicle.normalisePlate(request.plateNumber()).replace(" ", "");
                if (!seenPlates.add(key)) {
                    duplicates++;
                    errors.add(new ImportResult.RowError(row.rowNumber(), plate, "Duplicate plate number within the file"));
                    continue;
                }
                var existing = vehicleRepository.findByPlate(request.plateNumber());
                if (existing.isPresent()) {
                    if (updateExisting) {
                        vehicleService.update(existing.get().getId(), request);
                        updated++;
                    } else {
                        duplicates++;
                        errors.add(new ImportResult.RowError(row.rowNumber(), plate, "Vehicle already exists (plate " + request.plateNumber() + ")"));
                    }
                    continue;
                }
                vehicleService.create(request);
                imported++;
            } catch (DuplicateResourceException e) {
                duplicates++;
                errors.add(new ImportResult.RowError(row.rowNumber(), plate, e.getMessage()));
            } catch (RuntimeException e) {
                errors.add(new ImportResult.RowError(row.rowNumber(), plate, e.getMessage()));
            }
        }
        return new ImportResult("VEHICLES", file.getOriginalFilename(), rows.size(), imported, updated,
                rows.size() - imported - updated, duplicates, errors);
    }


    private VehicleRequest toRequest(TabularFileReader.TabularRow row) {
        String categoryValue = ImportSupport.required(row.get("category"), "category");
        VehicleCategory category = categoryRepository.findByCodeIgnoreCase(categoryValue)
                .or(() -> categoryRepository.findByNameIgnoreCase(categoryValue))
                .orElseThrow(() -> new IllegalArgumentException("Unknown vehicle category '" + categoryValue + "'"));
        return new VehicleRequest(
                ImportSupport.required(row.get("plate_number"), "plate_number"),
                row.get("fleet_number"),
                ImportSupport.required(row.get("make"), "make"),
                ImportSupport.required(row.get("model"), "model"),
                ImportSupport.parseInt(row.get("year"), "year"),
                category.getId(),
                row.get("body_type"),
                ImportSupport.parseEnum(row.get("fuel_type"), FuelType.class, "fuel_type", FuelType.DIESEL),
                ImportSupport.parseEnum(row.get("transmission"), Transmission.class, "transmission", null),
                row.get("engine_number"),
                row.get("chassis_number"),
                row.get("color"),
                ImportSupport.parseLong(row.get("odometer_km"), "odometer_km"),
                ImportSupport.parseInt(row.get("seating_capacity"), "seating_capacity"),
                ImportSupport.parseDate(row.get("purchase_date"), "purchase_date"),
                ImportSupport.parseDecimal(row.get("acquisition_cost"), "acquisition_cost"),
                ImportSupport.parseEnum(row.get("ownership_type"), OwnershipType.class, "ownership_type", OwnershipType.OWNED),
                row.get("owner_name"), null, null,
                row.get("insurance_provider"),
                row.get("insurance_policy_number"),
                ImportSupport.parseDate(row.get("insurance_expiry_date"), "insurance_expiry_date"),
                ImportSupport.parseDecimal(row.get("day_rate"), "day_rate"),
                row.get("department"),
                row.get("notes"));
    }
}
