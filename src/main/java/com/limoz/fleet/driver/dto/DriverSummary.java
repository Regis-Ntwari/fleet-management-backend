package com.limoz.fleet.driver.dto;

import com.limoz.fleet.driver.DriverStatus;

public record DriverSummary(Long id, String driverCode, String fullName, String phone, String licenseNumber, DriverStatus status) {}
