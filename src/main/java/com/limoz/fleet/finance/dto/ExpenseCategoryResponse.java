package com.limoz.fleet.finance.dto;

public record ExpenseCategoryResponse(Long id, String code, String name, boolean active, int sortOrder) {}
