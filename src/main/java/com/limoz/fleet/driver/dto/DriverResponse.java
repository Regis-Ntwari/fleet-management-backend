package com.limoz.fleet.driver.dto;

import com.limoz.fleet.driver.domain.DriverStatus;
import com.limoz.fleet.driver.domain.EmploymentStatus;

import java.time.Instant;
import java.time.LocalDate;

public record DriverResponse(
        Long id,
        String driverCode,
        String firstName,
        String lastName,
        String fullName,
        String phone,
        String email,
        String nationalId,
        String licenseNumber,
        String licenseCategory,
        LocalDate licenseIssueDate,
        LocalDate licenseExpiryDate,
        String licenseStatus,
        EmploymentStatus employmentStatus,
        DriverStatus status,
        Long currentVehicleId,
        String currentVehiclePlate,
        String basedIn,
        String emergencyContactName,
        String emergencyContactPhone,
        LocalDate joiningDate,
        LocalDate dateOfBirth,
        Long photoAttachmentId,
        String notes,
        boolean archived,
        Instant createdAt,
        Instant updatedAt) {}
