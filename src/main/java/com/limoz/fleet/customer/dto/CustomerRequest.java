package com.limoz.fleet.customer.dto;

import com.limoz.fleet.customer.CustomerType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CustomerRequest(
        @Size(max = 20) String customerCode,
        @NotBlank @Size(max = 150) String name,
        CustomerType customerType,
        @Size(max = 30) String tin,
        @Size(max = 120) String contactPerson,
        @Email @Size(max = 150) String email,
        @Size(max = 30) String phone,
        @Size(max = 255) String address,
        @Size(max = 80) String city,
        @Size(max = 80) String country,
        Long accountManagerUserId,
        @DecimalMin("0") BigDecimal creditLimit,
        Boolean active,
        String notes) {}
