package com.limoz.fleet.settings.dto;

import jakarta.validation.constraints.NotBlank;

public record SettingUpdateRequest(@NotBlank String key, String value) {}
