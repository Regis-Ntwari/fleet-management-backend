package com.limoz.fleet.vehicle.dto;

import com.limoz.fleet.vehicle.domain.OdometerSource;

import java.time.Instant;

public record OdometerLogResponse(Long id, long readingKm, Long previousKm, OdometerSource source, String referenceType,
                                  Long referenceId, String correctionReason, Instant recordedAt, String recordedBy) {}
