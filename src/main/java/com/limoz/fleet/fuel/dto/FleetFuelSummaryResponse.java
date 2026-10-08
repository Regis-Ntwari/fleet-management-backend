package com.limoz.fleet.fuel.dto;

import java.util.List;

/** Fleet-wide totals plus a per-vehicle breakdown, both aggregated in the database. */
public record FleetFuelSummaryResponse(FuelSummaryResponse totals, List<FuelSummaryResponse> vehicles) {}
