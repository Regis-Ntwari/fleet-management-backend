package com.limoz.fleet.dashboard.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One point of a time series: a day with a count and a numeric value (km, litres, cost, percent...). */
public record DailyPoint(LocalDate date, long count, BigDecimal value) {}
