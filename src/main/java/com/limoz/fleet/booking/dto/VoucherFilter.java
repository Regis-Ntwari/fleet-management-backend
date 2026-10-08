package com.limoz.fleet.booking.dto;

import com.limoz.fleet.booking.domain.VoucherStatus;

import java.time.LocalDate;
import java.util.List;

/** Voucher search filters; {@code from}/{@code to} apply to the voucher date. */
public record VoucherFilter(String q, List<VoucherStatus> status, Long customerId, Long vehicleId, Long driverId,
                            Long bookingId, Long slotId, LocalDate from, LocalDate to) {}
