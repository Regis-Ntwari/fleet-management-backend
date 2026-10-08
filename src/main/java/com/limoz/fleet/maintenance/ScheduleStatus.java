package com.limoz.fleet.maintenance;

/** Ordered from best to worst so the worst schedule of a vehicle can be found by comparison. */
public enum ScheduleStatus { OK, DUE_SOON, OVERDUE }
