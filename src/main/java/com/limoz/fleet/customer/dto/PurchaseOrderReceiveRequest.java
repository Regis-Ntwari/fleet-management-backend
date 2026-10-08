package com.limoz.fleet.customer.dto;

import java.time.LocalDate;

/** Date the signed LPO document was received from the client (defaults to today). */
public record PurchaseOrderReceiveRequest(LocalDate receivedDate, Long attachmentId) {}
