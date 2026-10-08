package com.limoz.fleet.telematics.dto;

import java.time.Instant;
import java.util.List;

/** Ordered position history of one vehicle. {@code truncated} is true when more rows exist than the cap allows. */
public record PositionSeriesResponse(Long vehicleId, String plateNumber, Instant from, Instant to, int count,
                                     long totalInWindow, boolean truncated, List<PositionResponse> positions) {}
