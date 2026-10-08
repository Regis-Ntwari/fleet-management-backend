package com.limoz.fleet.telematics.movement.dto;

import java.time.LocalDate;

public record RecomputeResponse(LocalDate date, int vehicles, int withPositions, int withTripsOnly, int withoutData) {}
