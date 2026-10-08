package com.limoz.fleet.booking.dto;

import com.limoz.fleet.booking.BookingStatus;

import java.time.LocalDate;
import java.util.List;

/**
 * Booking search filters. {@code from}/{@code to} select bookings whose period overlaps the range;
 * {@code readyFor} applies the screen tab (DEPLOYMENT = confirmed + ready for deployment, BILLING = ready for billing).
 */
public record BookingFilter(String q, List<BookingStatus> status, Long customerId, Long commitmentId,
                            LocalDate from, LocalDate to, BookingTab readyFor) {}
