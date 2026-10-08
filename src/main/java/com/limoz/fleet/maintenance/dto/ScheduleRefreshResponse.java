package com.limoz.fleet.maintenance.dto;

/** Outcome of a schedule refresh run. */
public record ScheduleRefreshResponse(int schedulesChecked, int schedulesChanged, int vehiclesUpdated, int dueSoon, int overdue) {}
