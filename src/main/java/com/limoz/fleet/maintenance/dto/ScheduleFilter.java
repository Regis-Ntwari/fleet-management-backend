package com.limoz.fleet.maintenance.dto;

import com.limoz.fleet.maintenance.domain.ScheduleStatus;

import java.util.List;

public record ScheduleFilter(List<ScheduleStatus> status, Long vehicleId, Long serviceTypeId, Boolean active) {}
