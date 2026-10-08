package com.limoz.fleet.booking.dto;

import com.limoz.fleet.booking.BookingStatus;

import java.util.Map;

/** Tab counters of the bookings screen plus a breakdown per status. */
public record BookingCounts(long all, long readyForDeployment, long readyForBilling, Map<BookingStatus, Long> byStatus) {}
