package com.limoz.fleet.settings.dto;

import com.limoz.fleet.settings.domain.SettingValueType;

import java.time.Instant;

public record SettingResponse(String key, String value, SettingValueType valueType, String category,
                              String description, boolean editable, String updatedBy, Instant updatedAt) {}
