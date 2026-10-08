package com.limoz.fleet.customer.dto;

import com.limoz.fleet.customer.PurchaseOrderStatus;

import java.time.LocalDate;
import java.util.List;

public record PurchaseOrderFilter(String q, Long customerId, Long commitmentId, Long bookingId, List<PurchaseOrderStatus> status,
                                  LocalDate from, LocalDate to) {}
