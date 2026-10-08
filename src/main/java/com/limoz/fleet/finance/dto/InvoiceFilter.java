package com.limoz.fleet.finance.dto;

import com.limoz.fleet.finance.domain.InvoiceStatus;

import java.time.LocalDate;
import java.util.List;

public record InvoiceFilter(String q, Long customerId, Long bookingId, List<InvoiceStatus> status, LocalDate from, LocalDate to, Boolean overdue) {}
