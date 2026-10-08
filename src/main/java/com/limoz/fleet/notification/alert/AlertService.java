package com.limoz.fleet.notification.alert;

import com.limoz.fleet.audit.AuditAction;
import com.limoz.fleet.audit.AuditService;
import com.limoz.fleet.common.api.PageResponse;
import com.limoz.fleet.common.exception.InvalidStateTransitionException;
import com.limoz.fleet.common.exception.ResourceNotFoundException;
import com.limoz.fleet.config.CacheConfig;
import com.limoz.fleet.notification.NotificationSeverity;
import com.limoz.fleet.notification.NotificationService;
import com.limoz.fleet.notification.alert.dto.AlertFilter;
import com.limoz.fleet.notification.alert.dto.AlertNoteRequest;
import com.limoz.fleet.notification.alert.dto.AlertResponse;
import com.limoz.fleet.notification.alert.dto.AlertScanResult;
import com.limoz.fleet.notification.alert.dto.AlertSummaryResponse;
import com.limoz.fleet.security.Roles;
import com.limoz.fleet.security.SecurityUtils;
import com.limoz.fleet.user.User;
import com.limoz.fleet.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Management alert centre. {@link #runScan()} collects {@link AlertCandidate}s from every {@link AlertScanner},
 * upserts them by dedupe key, re-raises resolved alerts whose condition is back, auto-resolves alerts whose condition
 * cleared, and notifies management of every new WARNING / CRITICAL alert.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AlertService {

    public static final String AUTO_RESOLUTION_NOTE = "Condition cleared";
    static final List<AlertStatus> OPEN = List.of(AlertStatus.ACTIVE, AlertStatus.ACKNOWLEDGED);
    private static final int TOP_TYPES = 5;

    private final AlertRepository alertRepository;
    private final List<AlertScanner> scanners;
    private final NotificationService notificationService;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final Clock clock;

    // ---------------------------------------------------------------- scanning

    @CacheEvict(cacheNames = CacheConfig.DASHBOARD, allEntries = true)
    public AlertScanResult runScan() {
        Instant now = Instant.now(clock);
        Map<String, AlertCandidate> candidates = new LinkedHashMap<>();
        List<String> failed = new ArrayList<>();
        for (AlertScanner scanner : scanners) {
            try {
                for (AlertCandidate candidate : scanner.scan()) {
                    if (candidate == null || candidate.dedupeKey() == null || candidate.dedupeKey().isBlank()) {
                        continue;
                    }
                    candidates.putIfAbsent(candidate.dedupeKey(), candidate);
                }
            } catch (RuntimeException ex) {
                failed.add(scanner.scannerName());
                log.error("Alert scanner {} failed: {}", scanner.scannerName(), ex.getMessage(), ex);
            }
        }

        Map<String, Alert> existing = candidates.isEmpty() ? Map.of()
                : alertRepository.findByDedupeKeyIn(candidates.keySet()).stream().collect(Collectors.toMap(Alert::getDedupeKey, Function.identity()));
        int created = 0;
        int reraised = 0;
        int refreshed = 0;
        int notifications = 0;
        for (AlertCandidate candidate : candidates.values()) {
            Alert alert = existing.get(candidate.dedupeKey());
            boolean isNew = false;
            if (alert == null) {
                alert = new Alert();
                alert.setDedupeKey(candidate.dedupeKey());
                alert.setFirstDetectedAt(now);
                created++;
                isNew = true;
            } else if (alert.getStatus() == AlertStatus.RESOLVED) {
                alert.setStatus(AlertStatus.ACTIVE);
                alert.setFirstDetectedAt(now);
                alert.setAcknowledgedAt(null);
                alert.setAcknowledgedByUserId(null);
                alert.setResolvedAt(null);
                alert.setResolutionNote(null);
                reraised++;
                isNew = true;
            } else {
                refreshed++;
            }
            apply(alert, candidate);
            alert.setLastDetectedAt(now);
            alertRepository.save(alert);
            if (isNew && candidate.severity().atLeast(NotificationSeverity.WARNING)) {
                notifications += notify(alert);
            }
        }

        int resolved = 0;
        if (failed.isEmpty()) {
            Set<String> seen = new HashSet<>(candidates.keySet());
            for (Alert alert : alertRepository.findByStatusIn(OPEN)) {
                if (!seen.contains(alert.getDedupeKey())) {
                    alert.setStatus(AlertStatus.RESOLVED);
                    alert.setResolvedAt(now);
                    alert.setResolutionNote(AUTO_RESOLUTION_NOTE);
                    alertRepository.save(alert);
                    resolved++;
                }
            }
        } else {
            log.warn("Auto-resolution skipped because {} scanner(s) failed: {}", failed.size(), failed);
        }
        AlertScanResult result = new AlertScanResult(now, scanners.size(), candidates.size(), created, reraised, refreshed, resolved,
                notifications, List.copyOf(failed));
        log.info("Alert scan: {}", result);
        return result;
    }

    private static void apply(Alert alert, AlertCandidate c) {
        alert.setType(c.type());
        alert.setSeverity(c.severity() == null ? NotificationSeverity.WARNING : c.severity());
        alert.setTitle(truncate(c.title(), 150));
        alert.setMessage(truncate(c.message() == null || c.message().isBlank() ? c.title() : c.message(), 1000));
        alert.setEntityType(truncate(c.entityType(), 40));
        alert.setEntityId(c.entityId());
        alert.setEntityReference(truncate(c.entityReference(), 120));
        alert.setLinkPath(truncate(c.linkPath(), 255));
    }

    private int notify(Alert alert) {
        Set<String> roles = new HashSet<>(Set.of(Roles.FLEET_MANAGER, Roles.MANAGEMENT));
        if (alert.getSeverity() == NotificationSeverity.CRITICAL && alert.getType().isGpsRelated()) {
            roles.add(Roles.IT_ADMIN);
        }
        String dedupeKey = "ALERT:" + alert.getDedupeKey() + ":" + LocalDate.ofInstant(alert.getFirstDetectedAt(), clock.getZone());
        return notificationService.notifyRoles(roles, alert.getType().name(), alert.getSeverity(), alert.getTitle(), alert.getMessage(),
                alert.getEntityType(), alert.getEntityId(), alert.getLinkPath(), dedupeKey);
    }

    // ---------------------------------------------------------------- manual handling

    @CacheEvict(cacheNames = CacheConfig.DASHBOARD, allEntries = true)
    public AlertResponse acknowledge(Long id, AlertNoteRequest request) {
        Alert alert = load(id);
        if (alert.getStatus() != AlertStatus.ACTIVE) {
            throw new InvalidStateTransitionException("Alert", alert.getStatus(), AlertStatus.ACKNOWLEDGED);
        }
        AlertResponse before = toResponse(alert, Map.of());
        alert.setStatus(AlertStatus.ACKNOWLEDGED);
        alert.setAcknowledgedAt(Instant.now(clock));
        alert.setAcknowledgedByUserId(SecurityUtils.currentUserId().orElse(null));
        if (request != null && request.note() != null && !request.note().isBlank()) {
            alert.setResolutionNote(truncate(request.note().trim(), 255));
        }
        AlertResponse after = toResponse(alertRepository.save(alert));
        auditService.record(AuditAction.STATUS_CHANGE, "Alert", id, alert.getEntityReference(), before, after,
                "Alert " + alert.getType() + " acknowledged" + note(request));
        return after;
    }

    @CacheEvict(cacheNames = CacheConfig.DASHBOARD, allEntries = true)
    public AlertResponse resolve(Long id, AlertNoteRequest request) {
        Alert alert = load(id);
        if (!alert.getStatus().isOpen()) {
            throw new InvalidStateTransitionException("Alert", alert.getStatus(), AlertStatus.RESOLVED);
        }
        AlertResponse before = toResponse(alert, Map.of());
        alert.setStatus(AlertStatus.RESOLVED);
        alert.setResolvedAt(Instant.now(clock));
        alert.setResolutionNote(request == null || request.note() == null || request.note().isBlank()
                ? "Resolved manually" : truncate(request.note().trim(), 255));
        AlertResponse after = toResponse(alertRepository.save(alert));
        auditService.record(AuditAction.STATUS_CHANGE, "Alert", id, alert.getEntityReference(), before, after,
                "Alert " + alert.getType() + " resolved manually" + note(request));
        return after;
    }

    // ---------------------------------------------------------------- queries

    @Transactional(readOnly = true)
    public PageResponse<AlertResponse> search(AlertFilter f, Pageable pageable) {
        Pageable unsorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        Page<Alert> page = alertRepository.search(f.status(), f.severity(), f.type(),
                f.entityType() == null || f.entityType().isBlank() ? null : f.entityType().trim(), unsorted);
        Map<Long, String> names = userNames(page.getContent());
        return PageResponse.from(page.map(a -> toResponse(a, names)));
    }

    @Transactional(readOnly = true)
    public AlertResponse get(Long id) {
        return toResponse(load(id));
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheConfig.DASHBOARD, key = "'alertSummary'")
    public AlertSummaryResponse summary() {
        Map<NotificationSeverity, Long> bySeverity = new EnumMap<>(NotificationSeverity.class);
        for (NotificationSeverity s : NotificationSeverity.values()) bySeverity.put(s, 0L);
        alertRepository.countBySeverity(OPEN).forEach(c -> bySeverity.put(c.getSeverity(), c.getTotal()));
        Map<AlertStatus, Long> byStatus = new EnumMap<>(AlertStatus.class);
        for (AlertStatus s : AlertStatus.values()) byStatus.put(s, 0L);
        alertRepository.countByStatus().forEach(c -> byStatus.put(c.getStatus(), c.getTotal()));
        List<AlertSummaryResponse.TypeCount> top = alertRepository.countByType(OPEN, PageRequest.of(0, TOP_TYPES)).stream()
                .map(c -> new AlertSummaryResponse.TypeCount(c.getType(), c.getTotal())).toList();
        long open = bySeverity.values().stream().mapToLong(Long::longValue).sum();
        return new AlertSummaryResponse(open, bySeverity.get(NotificationSeverity.CRITICAL), bySeverity.get(NotificationSeverity.WARNING),
                bySeverity.get(NotificationSeverity.INFO), bySeverity, byStatus, top, Instant.now(clock));
    }

    private Alert load(Long id) {
        return alertRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Alert", id));
    }

    private Map<Long, String> userNames(List<Alert> alerts) {
        Set<Long> ids = alerts.stream().map(Alert::getAcknowledgedByUserId).filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) return Map.of();
        Map<Long, String> names = new HashMap<>();
        for (User u : userRepository.findAllById(ids)) {
            names.put(u.getId(), u.getFullName());
        }
        return names;
    }

    private AlertResponse toResponse(Alert a) {
        return toResponse(a, userNames(List.of(a)));
    }

    private static AlertResponse toResponse(Alert a, Map<Long, String> userNames) {
        return new AlertResponse(a.getId(), a.getType(), a.getSeverity(), a.getTitle(), a.getMessage(), a.getEntityType(), a.getEntityId(),
                a.getEntityReference(), a.getLinkPath(), a.getStatus(), a.getFirstDetectedAt(), a.getLastDetectedAt(),
                a.getAcknowledgedByUserId(), a.getAcknowledgedByUserId() == null ? null : userNames.get(a.getAcknowledgedByUserId()),
                a.getAcknowledgedAt(), a.getResolvedAt(), a.getResolutionNote());
    }

    private static String note(AlertNoteRequest request) {
        return request == null || request.note() == null || request.note().isBlank() ? "" : ": " + request.note().trim();
    }

    private static String truncate(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }
}
