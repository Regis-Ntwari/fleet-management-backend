package com.limoz.fleet.settings.service;

import com.limoz.fleet.settings.domain.SystemSetting;
import com.limoz.fleet.settings.repository.SystemSettingRepository;

import com.limoz.fleet.audit.domain.AuditAction;
import com.limoz.fleet.audit.service.AuditService;
import com.limoz.fleet.common.exception.BusinessRuleException;
import com.limoz.fleet.common.exception.ResourceNotFoundException;
import com.limoz.fleet.config.CacheConfig;
import com.limoz.fleet.security.SecurityUtils;
import com.limoz.fleet.settings.dto.SettingResponse;
import com.limoz.fleet.settings.dto.SettingUpdateRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Typed access to runtime configuration. The full map is cached and evicted on any update.
 */
@Service
@RequiredArgsConstructor
public class SettingsService {

    private final SystemSettingRepository repository;
    private final AuditService auditService;
    private final Clock clock;

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheConfig.SETTINGS, key = "'all'")
    public Map<String, String> all() {
        return repository.findAll().stream()
                .collect(Collectors.toMap(SystemSetting::getKey, s -> s.getValue() == null ? "" : s.getValue()));
    }

    public String get(String key) {
        String value = all().get(key);
        if (value == null) {
            throw new IllegalStateException("Missing system setting: " + key + " (check migrations)");
        }
        return value;
    }

    public int getInt(String key) {
        return Integer.parseInt(get(key).trim());
    }

    /** Integer setting with a fallback for keys that may be absent on older databases. */
    public int getIntOrDefault(String key, int defaultValue) {
        String value = all().get(key);
        return value == null || value.isBlank() ? defaultValue : Integer.parseInt(value.trim());
    }

    public BigDecimal getDecimal(String key) {
        return new BigDecimal(get(key).trim());
    }

    public boolean getBoolean(String key) {
        return Boolean.parseBoolean(get(key).trim());
    }

    public LocalTime getTime(String key) {
        return LocalTime.parse(get(key).trim());
    }

    @Transactional(readOnly = true)
    public List<SettingResponse> list() {
        return repository.findAllByOrderByCategoryAscKeyAsc().stream().map(this::toResponse).toList();
    }

    @Transactional
    @CacheEvict(cacheNames = {CacheConfig.SETTINGS, CacheConfig.DASHBOARD}, allEntries = true)
    public List<SettingResponse> update(List<SettingUpdateRequest> updates) {
        Instant now = Instant.now(clock);
        String actor = SecurityUtils.currentUsername().orElse("system");
        for (SettingUpdateRequest update : updates) {
            SystemSetting setting = repository.findByKey(update.key())
                    .orElseThrow(() -> new ResourceNotFoundException("Setting " + update.key() + " does not exist"));
            if (!setting.isEditable()) {
                throw new BusinessRuleException("Setting " + update.key() + " is not editable");
            }
            validate(setting, update.value());
            String previous = setting.getValue();
            setting.setValue(update.value());
            setting.setUpdatedBy(actor);
            setting.setUpdatedAt(now);
            repository.save(setting);
            auditService.record(AuditAction.UPDATE, "SystemSetting", setting.getId(), setting.getKey(),
                    Map.of("value", previous == null ? "" : previous), Map.of("value", update.value() == null ? "" : update.value()),
                    "Setting " + setting.getKey() + " changed");
        }
        return list();
    }

    private void validate(SystemSetting setting, String value) {
        if (value == null) {
            throw new BusinessRuleException("Setting " + setting.getKey() + " requires a value");
        }
        try {
            switch (setting.getValueType()) {
                case INTEGER -> Integer.parseInt(value.trim());
                case DECIMAL -> new BigDecimal(value.trim());
                case BOOLEAN -> {
                    if (!value.trim().equalsIgnoreCase("true") && !value.trim().equalsIgnoreCase("false")) {
                        throw new IllegalArgumentException();
                    }
                }
                case TIME -> LocalTime.parse(value.trim());
                default -> { }
            }
        } catch (RuntimeException ex) {
            throw new BusinessRuleException("INVALID_SETTING_VALUE",
                    "Value '" + value + "' is not a valid " + setting.getValueType() + " for " + setting.getKey());
        }
    }

    private SettingResponse toResponse(SystemSetting s) {
        return new SettingResponse(s.getKey(), s.getValue(), s.getValueType(), s.getCategory(), s.getDescription(),
                s.isEditable(), s.getUpdatedBy(), s.getUpdatedAt());
    }
}
