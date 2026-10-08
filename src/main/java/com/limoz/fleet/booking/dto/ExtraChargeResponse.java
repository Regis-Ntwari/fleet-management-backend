package com.limoz.fleet.booking.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record ExtraChargeResponse(Long id, String description, BigDecimal amount, Instant createdAt, String createdBy) {}
