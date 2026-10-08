package com.limoz.fleet.maintenance.dto;

import com.limoz.fleet.maintenance.ScheduleStatus;

import java.util.List;

public record ScheduleFilter(List<ScheduleStatus> status, Long vehicleId, Long serviceTypeId, Boolean active) {}
