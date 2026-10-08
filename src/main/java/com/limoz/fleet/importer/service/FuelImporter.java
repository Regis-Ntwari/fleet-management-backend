package com.limoz.fleet.importer.service;

import com.limoz.fleet.common.exception.DuplicateResourceException;
import com.limoz.fleet.driver.repository.DriverRepository;
import com.limoz.fleet.fuel.domain.FuelPaymentMethod;
import com.limoz.fleet.fuel.dto.FuelTransactionRequest;
import com.limoz.fleet.fuel.service.FuelService;
import com.limoz.fleet.importer.domain.ImportSupport;
import com.limoz.fleet.importer.dto.ImportResult;
import com.limoz.fleet.importer.repository.TabularFileReader;
import com.limoz.fleet.vehicle.domain.FuelType;
import com.limoz.fleet.vehicle.domain.Vehicle;
import com.limoz.fleet.vehicle.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Fuel transaction import (fuel-card statements, station reports). Columns: plate_number*, date* (yyyy-MM-dd or dd/MM/yyyy),
 * time (HH:mm), station*, litres*, price_per_litre*, odometer_km*, receipt_number, driver_licence, fuel_type, sensor_litres,
 * supplier, payment_method, notes. Duplicates (same vehicle + receipt number, or identical plate/date/time/litres within the file)
 * are rejected, never silently merged.
 */
@Component
@RequiredArgsConstructor
public class FuelImporter {

    public static final List<String> COLUMNS = List.of("plate_number", "date", "time", "station", "litres", "price_per_litre", "odometer_km",
            "receipt_number", "driver_licence", "fuel_type", "sensor_litres", "supplier", "payment_method", "notes");

    private final TabularFileReader reader;
    private final FuelService fuelService;
    private final VehicleRepository vehicleRepository;
    private final DriverRepository driverRepository;
    private final ZoneId zone;

    public ImportResult importFile(MultipartFile file) {
        List<TabularFileReader.TabularRow> rows = reader.read(file);
        List<ImportResult.RowError> errors = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int imported = 0, duplicates = 0;
        for (TabularFileReader.TabularRow row : rows) {
            String plate = row.get("plate_number");
            try {
                FuelTransactionRequest request = toRequest(row);
                String key = plate + "|" + row.get("date") + "|" + row.get("time") + "|" + request.litres();
                if (!seen.add(key.toUpperCase())) {
                    duplicates++;
                    errors.add(new ImportResult.RowError(row.rowNumber(), plate, "Duplicate transaction within the file"));
                    continue;
                }
                fuelService.create(request);
                imported++;
            } catch (DuplicateResourceException e) {
                duplicates++;
                errors.add(new ImportResult.RowError(row.rowNumber(), plate, e.getMessage()));
            } catch (RuntimeException e) {
                errors.add(new ImportResult.RowError(row.rowNumber(), plate, e.getMessage()));
            }
        }
        return new ImportResult("FUEL", file.getOriginalFilename(), rows.size(), imported, 0, rows.size() - imported, duplicates, errors);
    }

    private FuelTransactionRequest toRequest(TabularFileReader.TabularRow row) {
        String plate = ImportSupport.required(row.get("plate_number"), "plate_number");
        Vehicle vehicle = vehicleRepository.findByPlate(plate).orElseThrow(() -> new IllegalArgumentException("Unknown vehicle plate '" + plate + "'"));
        LocalDate date = ImportSupport.parseDate(ImportSupport.required(row.get("date"), "date"), "date");
        LocalTime time = row.get("time") == null ? LocalTime.NOON : LocalTime.parse(row.get("time").length() == 4 ? "0" + row.get("time") : row.get("time"));
        Long driverId = null;
        String licence = row.get("driver_licence");
        if (licence != null) {
            driverId = driverRepository.findByLicenseNumberIgnoreCase(licence).orElseThrow(() -> new IllegalArgumentException("Unknown driver licence '" + licence + "'")).getId();
        } else if (vehicle.getCurrentDriver() != null) {
            driverId = vehicle.getCurrentDriver().getId();
        }
        BigDecimal litres = ImportSupport.parseDecimal(ImportSupport.required(row.get("litres"), "litres"), "litres");
        BigDecimal price = ImportSupport.parseDecimal(ImportSupport.required(row.get("price_per_litre"), "price_per_litre"), "price_per_litre");
        Long odometer = ImportSupport.parseLong(ImportSupport.required(row.get("odometer_km"), "odometer_km"), "odometer_km");
        return new FuelTransactionRequest(vehicle.getId(), driverId, null, null, date.atTime(time).atZone(zone).toInstant(),
                ImportSupport.required(row.get("station"), "station"), row.get("supplier"),
                ImportSupport.parseEnum(row.get("fuel_type"), FuelType.class, "fuel_type", vehicle.getFuelType()), litres, price, "RWF", odometer,
                ImportSupport.parseDecimal(row.get("sensor_litres"), "sensor_litres"), true, row.get("receipt_number"), null,
                ImportSupport.parseEnum(row.get("payment_method"), FuelPaymentMethod.class, "payment_method", null), row.get("notes"));
    }
}
