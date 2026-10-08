package com.limoz.fleet.finance.dto;

import com.limoz.fleet.finance.domain.PaymentDirection;
import com.limoz.fleet.finance.domain.PaymentMethod;

import java.time.LocalDate;

public record PaymentFilter(String q, PaymentDirection direction, PaymentMethod method, Long customerId, Long invoiceId, Long expenseId,
                            Long trafficFineId, Long maintenanceRecordId, LocalDate from, LocalDate to, Boolean includeReversed) {}
