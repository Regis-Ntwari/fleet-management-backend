package com.limoz.fleet.notification.service;

import com.limoz.fleet.notification.domain.Notification;
import com.limoz.fleet.notification.repository.NotificationRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * The in-app channel: persists the notification so it shows up in the recipient's inbox (bell icon).
 * This is the only channel implemented today; see {@link NotificationChannel} for how e-mail, SMS and WhatsApp
 * channels plug in alongside it.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InAppNotificationChannel implements NotificationChannel {

    public static final String CODE = "in-app";

    private final NotificationRepository repository;

    @Override
    public String channelCode() {
        return CODE;
    }

    @Override
    public void deliver(Notification n) {
        int inserted = repository.insertIfAbsent(n.getRecipientUserId(), n.getType(), n.getSeverity().name(), n.getTitle(), n.getMessage(),
                n.getEntityType(), n.getEntityId(), n.getLinkPath(), n.getDedupeKey(), n.getCreatedAt());
        if (inserted == 0) {
            log.debug("Notification {} for user {} skipped: duplicate dedupe key {}", n.getType(), n.getRecipientUserId(), n.getDedupeKey());
        }
    }
}
