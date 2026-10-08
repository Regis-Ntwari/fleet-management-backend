package com.limoz.fleet.incident.dto;

import com.limoz.fleet.incident.IncidentStatus;
import com.limoz.fleet.incident.IncidentUpdateType;

import java.time.Instant;

public record IncidentUpdateResponse(Long id, IncidentUpdateType updateType, IncidentStatus fromStatus, IncidentStatus toStatus, String note,
                                     Long userId, String authorName, Instant createdAt) {}
