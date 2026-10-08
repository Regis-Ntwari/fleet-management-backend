package com.limoz.fleet.audit.service;

import com.limoz.fleet.audit.domain.AuditAction;
import com.limoz.fleet.audit.domain.AuditLog;
import com.limoz.fleet.audit.repository.AuditLogRepository;

import com.limoz.fleet.common.util.RequestContext;
import com.limoz.fleet.security.AuthenticatedUser;
import com.limoz.fleet.security.SecurityUtils;
import com.limoz.fleet.user.domain.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;

/**
 * Explicit, service-driven audit trail. Entries are written in the caller's transaction so an audit
 * record never exists without the change it describes (and vice versa).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository repository;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    @Transactional(propagation = Propagation.REQUIRED)
    public void record(AuditAction action, String entityType, Long entityId, String reference,
                       Object previous, Object next, String description) {
        AuthenticatedUser actor = SecurityUtils.currentUser().orElse(null);
        write(action, entityType, entityId, reference, previous, next, description,
                actor == null ? null : actor.id(), actor == null ? "system" : actor.email());
    }

    /** Variant used when the actor is known but not yet in the security context (login). */
    @Transactional(propagation = Propagation.REQUIRED)
    public void record(AuditAction action, String entityType, Long entityId, String reference,
                       Object previous, Object next, String description, User actor) {
        write(action, entityType, entityId, reference, previous, next, description, actor.getId(), actor.getEmail());
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void recordSystem(AuditAction action, String entityType, Long entityId, String reference, String description) {
        write(action, entityType, entityId, reference, null, null, description, null, "system");
    }

    private void write(AuditAction action, String entityType, Long entityId, String reference,
                       Object previous, Object next, String description, Long userId, String username) {
        AuditLog entry = new AuditLog();
        entry.setAction(action);
        entry.setEntityType(entityType);
        entry.setEntityId(entityId);
        entry.setEntityReference(reference);
        entry.setDescription(description != null && description.length() > 500 ? description.substring(0, 500) : description);
        entry.setPreviousValue(toJson(previous));
        entry.setNewValue(toJson(next));
        entry.setUserId(userId);
        entry.setUsername(username);
        entry.setIpAddress(RequestContext.clientIp().orElse(null));
        entry.setUserAgent(RequestContext.userAgent().map(ua -> ua.length() > 255 ? ua.substring(0, 255) : ua).orElse(null));
        entry.setOccurredAt(Instant.now(clock));
        repository.save(entry);
    }

    private String toJson(Object value) {
        if (value == null) return null;
        try {
            return jsonMapper.writeValueAsString(value);
        } catch (RuntimeException ex) {
            log.warn("Could not serialise audit payload of type {}", value.getClass().getSimpleName(), ex);
            return "{\"error\":\"unserialisable\"}";
        }
    }
}
