package com.limoz.fleet.maintenance.dto;

import jakarta.validation.constraints.Size;

/** Optional note accompanying a wait-for-parts / resume transition. */
public record MaintenanceNoteRequest(@Size(max = 500) String note) {}
