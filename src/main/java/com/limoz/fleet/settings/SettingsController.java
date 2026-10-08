package com.limoz.fleet.settings;

import com.limoz.fleet.settings.dto.SettingResponse;
import com.limoz.fleet.settings.dto.SettingUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/settings")
@RequiredArgsConstructor
@Tag(name = "System Settings", description = "Company profile and operational thresholds")
public class SettingsController {

    private final SettingsService settingsService;

    @GetMapping
    @PreAuthorize("hasAuthority('SETTINGS_MANAGE')")
    @Operation(summary = "List all settings with metadata")
    public List<SettingResponse> list() {
        return settingsService.list();
    }

    @GetMapping("/company")
    @Operation(summary = "Company profile (name, TIN, contacts, currency, timezone) - readable by all users")
    public Map<String, String> company() {
        return settingsService.all().entrySet().stream()
                .filter(e -> e.getKey().startsWith("company."))
                .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    @PutMapping
    @PreAuthorize("hasAuthority('SETTINGS_MANAGE')")
    @Operation(summary = "Update one or more settings")
    public List<SettingResponse> update(@Valid @RequestBody List<@Valid SettingUpdateRequest> updates) {
        return settingsService.update(updates);
    }
}
