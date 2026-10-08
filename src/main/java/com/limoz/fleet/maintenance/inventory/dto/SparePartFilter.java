package com.limoz.fleet.maintenance.inventory.dto;

import com.limoz.fleet.maintenance.inventory.StockStatus;

public record SparePartFilter(String q, StockStatus status, String category, String supplier, Boolean active) {}
