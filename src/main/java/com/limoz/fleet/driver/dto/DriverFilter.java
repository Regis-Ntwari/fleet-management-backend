package com.limoz.fleet.driver.dto;

import com.limoz.fleet.driver.domain.DriverStatus;
import com.limoz.fleet.driver.domain.EmploymentStatus;

import java.util.List;

public record DriverFilter(String q, List<DriverStatus> status, EmploymentStatus employmentStatus, String basedIn,
                           Boolean licenseExpired, Boolean archived) {}
