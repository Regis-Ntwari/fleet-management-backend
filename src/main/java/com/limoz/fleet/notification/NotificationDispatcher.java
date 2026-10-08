package com.limoz.fleet.notification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Fans a notification out to every registered {@link NotificationChannel}. A failing channel is logged and does not
 * prevent the other channels (in particular the in-app inbox) from delivering.
 */
@Slf4j
@Component
public class NotificationDispatcher {

    private final List<NotificationChannel> channels;

    public NotificationDispatcher(List<NotificationChannel> channels) {
        this.channels = List.copyOf(channels);
        log.info("Notification channels: {}", this.channels.stream().map(NotificationChannel::channelCode).toList());
    }

    public void dispatch(Notification notification) {
        for (NotificationChannel channel : channels) {
            try {
                channel.deliver(notification);
            } catch (RuntimeException ex) {
                log.error("Notification channel {} failed for user {} ({}): {}", channel.channelCode(),
                        notification.getRecipientUserId(), notification.getType(), ex.getMessage(), ex);
            }
        }
    }

    public List<String> channelCodes() {
        return channels.stream().map(NotificationChannel::channelCode).toList();
    }
}
