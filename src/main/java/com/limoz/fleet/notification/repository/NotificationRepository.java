package com.limoz.fleet.notification.repository;

import com.limoz.fleet.notification.domain.Notification;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findByRecipientUserIdOrderByCreatedAtDesc(Long recipientUserId, Pageable pageable);

    Page<Notification> findByRecipientUserIdAndReadAtIsNullOrderByCreatedAtDesc(Long recipientUserId, Pageable pageable);

    long countByRecipientUserIdAndReadAtIsNull(Long recipientUserId);

    Optional<Notification> findByIdAndRecipientUserId(Long id, Long recipientUserId);

    @Query("select n.recipientUserId from Notification n where n.dedupeKey = :key and n.recipientUserId in :userIds")
    Set<Long> recipientsAlreadyNotified(@Param("key") String key, @Param("userIds") Collection<Long> userIds);

    @Modifying
    @Query("update Notification n set n.readAt = :now where n.recipientUserId = :userId and n.readAt is null")
    int markAllRead(@Param("userId") Long userId, @Param("now") Instant now);

    /**
     * Inserts unless a notification with the same recipient and dedupe key already exists (partial unique index
     * {@code ux_notifications_dedupe}); returns 1 when inserted, 0 when skipped. Race-safe between concurrent scans.
     */
    @Modifying
    @Query(value = "insert into notifications (recipient_user_id, notification_type, severity, title, message, entity_type, entity_id, "
            + "link_path, dedupe_key, created_at) values (:recipientId, :type, :severity, :title, :message, :entityType, :entityId, "
            + ":linkPath, :dedupeKey, :createdAt) on conflict do nothing", nativeQuery = true)
    int insertIfAbsent(@Param("recipientId") Long recipientId, @Param("type") String type, @Param("severity") String severity,
                       @Param("title") String title, @Param("message") String message, @Param("entityType") String entityType,
                       @Param("entityId") Long entityId, @Param("linkPath") String linkPath, @Param("dedupeKey") String dedupeKey,
                       @Param("createdAt") Instant createdAt);
}
