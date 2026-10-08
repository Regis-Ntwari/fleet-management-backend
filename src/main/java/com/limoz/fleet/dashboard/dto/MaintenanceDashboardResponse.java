package com.limoz.fleet.dashboard.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record MaintenanceDashboardResponse(LocalDate from, LocalDate to, long openJobs, long inWorkshop, long waitingForParts,
                                           long completedInRange, BigDecimal costInRange, long dueSoon, long overdue,
                                           List<CountByLabel> byStatus, List<CountByLabel> byType, List<DailyPoint> monthlyCost,
                                           List<CountByLabel> topVehiclesByCost) {}
