package com.limoz.fleet.finance.dto;

import com.limoz.fleet.finance.ExpenseStatus;

import java.time.LocalDate;
import java.util.List;

public record ExpenseFilter(String q, Long categoryId, List<ExpenseStatus> status, Long vehicleId, Long driverId, LocalDate from, LocalDate to,
                            Long submittedByUserId) {}
