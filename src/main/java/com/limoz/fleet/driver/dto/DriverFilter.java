package com.limoz.fleet.driver.dto;

import com.limoz.fleet.driver.DriverStatus;
import com.limoz.fleet.driver.EmploymentStatus;

import java.util.List;

public record DriverFilter(String q, List<DriverStatus> status, EmploymentStatus employmentStatus, String basedIn,
                           Boolean licenseExpired, Boolean archived) {}
