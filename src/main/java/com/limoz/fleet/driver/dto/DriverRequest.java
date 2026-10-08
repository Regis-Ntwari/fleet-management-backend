package com.limoz.fleet.driver.dto;

import com.limoz.fleet.driver.domain.EmploymentStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record DriverRequest(
        @NotBlank @Size(max = 80) String firstName,
        @NotBlank @Size(max = 80) String lastName,
        @Size(max = 30) String phone,
        @Email @Size(max = 150) String email,
        @Size(max = 30) String nationalId,
        @NotBlank(message = "Driving licence number is required") @Size(max = 40) String licenseNumber,
        @Size(max = 20) String licenseCategory,
        LocalDate licenseIssueDate,
        LocalDate licenseExpiryDate,
        EmploymentStatus employmentStatus,
        @Size(max = 80) String basedIn,
        @Size(max = 120) String emergencyContactName,
        @Size(max = 30) String emergencyContactPhone,
        LocalDate joiningDate,
        LocalDate dateOfBirth,
        Long photoAttachmentId,
        String notes) {}
