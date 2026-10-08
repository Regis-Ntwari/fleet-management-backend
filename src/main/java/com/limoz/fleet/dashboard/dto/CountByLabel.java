package com.limoz.fleet.dashboard.dto;

import java.math.BigDecimal;

public record CountByLabel(String label, long count, BigDecimal value) {}
