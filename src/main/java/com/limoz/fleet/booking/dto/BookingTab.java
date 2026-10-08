package com.limoz.fleet.booking.dto;

import com.limoz.fleet.booking.domain.BookingStatus;

import java.util.List;

/** Tab filters of the bookings screen ("Ready for Deployment", "Ready for Billing"). */
public enum BookingTab {
    DEPLOYMENT(List.of(BookingStatus.CONFIRMED, BookingStatus.READY_FOR_DEPLOYMENT)),
    BILLING(List.of(BookingStatus.READY_FOR_BILLING));

    private final List<BookingStatus> statuses;

    BookingTab(List<BookingStatus> statuses) {
        this.statuses = statuses;
    }

    public List<BookingStatus> statuses() {
        return statuses;
    }
}
