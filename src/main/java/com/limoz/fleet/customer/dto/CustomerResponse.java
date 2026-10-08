package com.limoz.fleet.customer.dto;

import com.limoz.fleet.customer.domain.CustomerType;

import java.math.BigDecimal;
import java.time.Instant;

public record CustomerResponse(Long id, String customerCode, String name, CustomerType customerType, String tin, String contactPerson,
                               String email, String phone, String address, String city, String country, Long accountManagerUserId,
                               BigDecimal creditLimit, boolean active, String notes, Instant createdAt, Instant updatedAt) {}
