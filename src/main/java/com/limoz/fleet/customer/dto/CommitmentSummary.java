package com.limoz.fleet.customer.dto;

import com.limoz.fleet.customer.CommitmentStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Compact commitment reference for dropdowns and embedding in other modules' responses. */
public record CommitmentSummary(Long id, String reference, String title, Long customerId, LocalDate periodStart, LocalDate periodEnd,
                                BigDecimal contractedValue, String currency, CommitmentStatus status) {}
