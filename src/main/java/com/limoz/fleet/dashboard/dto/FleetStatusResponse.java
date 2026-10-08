package com.limoz.fleet.dashboard.dto;

import java.util.List;

public record FleetStatusResponse(long total, List<CountByLabel> byStatus, List<CountByLabel> byCategory, List<CountByLabel> byMaintenanceStatus) {}
