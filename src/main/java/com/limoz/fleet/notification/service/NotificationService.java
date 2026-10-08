package com.limoz.fleet.notification.service;

import com.limoz.fleet.notification.domain.Notification;
import com.limoz.fleet.notification.domain.NotificationSeverity;
import com.limoz.fleet.notification.repository.NotificationRepository;

import com.limoz.fleet.common.api.PageResponse;
import com.limoz.fleet.common.exception.ResourceNotFoundException;
import com.limoz.fleet.notification.dto.NotificationResponse;
import com.limoz.fleet.notification.dto.UnreadCountResponse;
import com.limoz.fleet.security.Roles;
import com.limoz.fleet.security.SecurityUtils;
import com.limoz.fleet.user.domain.User;
import com.limoz.fleet.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Creates notifications for users or roles and serves each user's own inbox. Other modules never call this
 * service directly: they publish {@link com.limoz.fleet.common.event.OperationalEvent}s which
 * {@link OperationalEventListener} turns into notifications.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class NotificationService {

    static final Set<String> DEFAULT_ROLES = Set.of(Roles.FLEET_MANAGER, Roles.MANAGEMENT, Roles.SUPER_ADMIN);

    private final NotificationRepository repository;
    private final NotificationDispatcher dispatcher;
    private final UserRepository userRepository;
    private final Clock clock;

    /**
     * Notifies the given users. With a {@code dedupeKey}, recipients that already received a notification with the
     * same key are skipped. Returns the number of notifications created.
     */
    public int notifyUsers(Collection<Long> userIds, String type, NotificationSeverity severity, String title, String message,
                           String entityType, Long entityId, String linkPath, String dedupeKey) {
        if (userIds == null || userIds.isEmpty()) {
            return 0;
        }
        Set<Long> recipients = new LinkedHashSet<>(userIds);
        String key = truncate(dedupeKey, 200);
        if (key != null) {
            recipients.removeAll(repository.recipientsAlreadyNotified(key, recipients));
        }
        Instant now = Instant.now(clock);
        int created = 0;
        for (Long userId : recipients) {
            Notification n = new Notification();
            n.setRecipientUserId(userId);
            n.setType(truncate(type, 40));
            n.setSeverity(severity == null ? NotificationSeverity.INFO : severity);
            n.setTitle(truncate(title, 150));
            n.setMessage(truncate(message == null || message.isBlank() ? title : message, 1000));
            n.setEntityType(truncate(entityType, 40));
            n.setEntityId(entityId);
            n.setLinkPath(truncate(linkPath, 255));
            n.setDedupeKey(key);
            n.setCreatedAt(now);
            dispatcher.dispatch(n);
            created++;
        }
        return created;
    }

    /**
     * Notifies every active user holding one of the roles (empty / null roles = fleet managers, management and
     * super administrators). Returns the number of notifications created.
     */
    public int notifyRoles(Set<String> roles, String type, NotificationSeverity severity, String title, String message,
                           String entityType, Long entityId, String linkPath, String dedupeKey) {
        Set<String> targets = roles == null || roles.isEmpty() ? DEFAULT_ROLES : roles;
        Set<Long> userIds = new LinkedHashSet<>();
        for (User user : userRepository.findActiveByRoleCodes(targets)) {
            userIds.add(user.getId());
        }
        if (userIds.isEmpty()) {
            log.debug("No active users hold roles {} - notification {} not delivered", targets, type);
            return 0;
        }
        return notifyUsers(userIds, type, severity, title, message, entityType, entityId, linkPath, dedupeKey);
    }

    // ---------------------------------------------------------------- own inbox

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> myNotifications(boolean unreadOnly, Pageable pageable) {
        Long me = currentUserId();
        var page = unreadOnly
                ? repository.findByRecipientUserIdAndReadAtIsNullOrderByCreatedAtDesc(me, pageable)
                : repository.findByRecipientUserIdOrderByCreatedAtDesc(me, pageable);
        return PageResponse.from(page.map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public UnreadCountResponse unreadCount() {
        return new UnreadCountResponse(repository.countByRecipientUserIdAndReadAtIsNull(currentUserId()));
    }

    public NotificationResponse markRead(Long id) {
        Notification n = loadOwn(id);
        if (n.getReadAt() == null) {
            n.setReadAt(Instant.now(clock));
            repository.save(n);
        }
        return toResponse(n);
    }

    public int markAllRead() {
        return repository.markAllRead(currentUserId(), Instant.now(clock));
    }

    public void delete(Long id) {
        repository.delete(loadOwn(id));
    }

    /** Only the recipient can see a notification; anyone else gets 404 so ids cannot be probed. */
    private Notification loadOwn(Long id) {
        return repository.findByIdAndRecipientUserId(id, currentUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Notification", id));
    }

    private static Long currentUserId() {
        return SecurityUtils.requireCurrentUser().id();
    }

    public NotificationResponse toResponse(Notification n) {
        return new NotificationResponse(n.getId(), n.getType(), n.getSeverity(), n.getTitle(), n.getMessage(), n.getEntityType(),
                n.getEntityId(), n.getLinkPath(), n.isRead(), n.getReadAt(), n.getCreatedAt());
    }

    private static String truncate(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }
}
