package com.limoz.fleet.finance.dto;

import java.math.BigDecimal;

public record InvoiceLineResponse(Long id, Long bookingLineId, Long deploymentVoucherId, String description, BigDecimal quantity,
                                  BigDecimal unitPrice, BigDecimal taxPercent, BigDecimal taxAmount, BigDecimal lineTotal, int sortOrder) {}
