package com.limoz.fleet.notification;

/**
 * A delivery channel for notifications. Every Spring bean implementing this interface is picked up by
 * {@link NotificationDispatcher}, which hands each new notification to every channel in turn.
 * <p>
 * Adding a channel (e-mail, SMS, WhatsApp) therefore needs no change to the notification module: implement this
 * interface in a {@code @Component} (for example {@code EmailNotificationChannel} backed by
 * {@code spring-boot-starter-mail}, {@code SmsNotificationChannel} calling an SMS gateway, or
 * {@code WhatsAppNotificationChannel} using the WhatsApp Business API), resolve the recipient's address from
 * {@code recipientUserId} through {@code UserRepository}, honour the user's channel preferences, and guard the bean
 * with {@code @ConditionalOnProperty} so it only activates when its credentials are configured.
 * Channels must be idempotent-tolerant and must not throw for transient delivery errors: log and return.
 */
public interface NotificationChannel {

    /** Short channel identifier, e.g. "in-app", "email", "sms", "whatsapp". */
    String channelCode();

    /** Delivers one notification to its recipient. Called inside the notification transaction. */
    void deliver(Notification notification);
}
