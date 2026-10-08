package com.limoz.fleet.support;

import com.limoz.fleet.driver.DriverService;
import com.limoz.fleet.driver.EmploymentStatus;
import com.limoz.fleet.driver.dto.DriverRequest;
import com.limoz.fleet.driver.dto.DriverResponse;
import com.limoz.fleet.vehicle.FuelType;
import com.limoz.fleet.vehicle.OwnershipType;
import com.limoz.fleet.vehicle.Transmission;
import com.limoz.fleet.vehicle.VehicleCategoryRepository;
import com.limoz.fleet.vehicle.VehicleService;
import com.limoz.fleet.vehicle.dto.VehicleRequest;
import com.limoz.fleet.vehicle.dto.VehicleResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;

/** Builders for realistic test fixtures. Each call produces unique plates / licences. */
@Component
@RequiredArgsConstructor
public class TestData {

    private static final AtomicInteger SEQ = new AtomicInteger(100);

    private final VehicleService vehicleService;
    private final DriverService driverService;
    private final VehicleCategoryRepository categoryRepository;

    public Long anyCategoryId() {
        return categoryRepository.findByCodeIgnoreCase("MINIBUS").orElseThrow().getId();
    }

    public VehicleRequest vehicleRequest(String plate) {
        return new VehicleRequest(plate, null, "Toyota", "Hiace", 2022, anyCategoryId(), "Van", FuelType.DIESEL,
                Transmission.MANUAL, "ENG" + SEQ.incrementAndGet(), "CHS" + SEQ.incrementAndGet(), "White", 15000L, 16,
                LocalDate.of(2022, 3, 1), new BigDecimal("42000000"), OwnershipType.OWNED, null, null, null,
                "Radiant Insurance", "POL-" + SEQ.get(), LocalDate.now().plusMonths(6), new BigDecimal("160000"), "Operations", null);
    }

    public VehicleResponse vehicle() {
        int n = SEQ.incrementAndGet();
        return vehicleService.create(vehicleRequest("RAD " + n + " T"));
    }

    public DriverRequest driverRequest(LocalDate licenseExpiry) {
        int n = SEQ.incrementAndGet();
        return new DriverRequest("Driver" + n, "Tester", "+25078800" + n, "driver" + n + "@test.limoz.rw", "1199" + n,
                "RW-DL-" + n, "B", LocalDate.now().minusYears(2), licenseExpiry, EmploymentStatus.FULL_TIME, "Kigali",
                "Next of kin", "+250788000000", LocalDate.now().minusYears(1), LocalDate.of(1990, 1, 1), null, null);
    }

    public DriverResponse driver() {
        return driverService.create(driverRequest(LocalDate.now().plusYears(2)));
    }

    public DriverResponse driverWithExpiredLicense() {
        return driverService.create(driverRequest(LocalDate.now().minusDays(1)));
    }
}
