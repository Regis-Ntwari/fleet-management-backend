package com.limoz.fleet.vehicle.timeline;

import java.time.Instant;

/**
 * One event on a vehicle's activity timeline (driver assigned, trip started, refuelled, maintenance reported...).
 *
 * @param at         when it happened
 * @param category   ASSIGNMENT, TRIP, FUEL, MAINTENANCE, INCIDENT, DOCUMENT, STATUS, ODOMETER, DEPLOYMENT, FINE...
 * @param title      short headline ("Trip started")
 * @param detail     human detail ("Kigali -> Huye · driver J. Uwimana")
 * @param entityType related record type
 * @param entityId   related record id
 * @param reference  related record reference (trip number...)
 * @param linkPath   frontend route
 */
public record TimelineEntry(Instant at, String category, String title, String detail, String entityType, Long entityId,
                            String reference, String linkPath) {}
