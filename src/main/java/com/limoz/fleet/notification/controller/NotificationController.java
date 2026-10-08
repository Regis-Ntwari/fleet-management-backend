package com.limoz.fleet.notification.controller;

import com.limoz.fleet.notification.service.NotificationService;

import com.limoz.fleet.common.api.PageResponse;
import com.limoz.fleet.notification.dto.NotificationResponse;
import com.limoz.fleet.notification.dto.UnreadCountResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Every endpoint operates on the calling user's own notifications only. */
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "In-app inbox of the current user")
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    @PreAuthorize("hasAuthority('NOTIFICATION_READ')")
    @Operation(summary = "My notifications, newest first (unreadOnly=true to hide read ones)")
    public PageResponse<NotificationResponse> list(@RequestParam(defaultValue = "false") boolean unreadOnly,
                                                   @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return notificationService.myNotifications(unreadOnly, pageable);
    }

    @GetMapping("/unread-count")
    @PreAuthorize("hasAuthority('NOTIFICATION_READ')")
    @Operation(summary = "Number of unread notifications (bell badge)")
    public UnreadCountResponse unreadCount() {
        return notificationService.unreadCount();
    }

    @PostMapping("/{id}/read")
    @PreAuthorize("hasAuthority('NOTIFICATION_READ')")
    @Operation(summary = "Mark one of my notifications as read")
    public NotificationResponse markRead(@PathVariable Long id) {
        return notificationService.markRead(id);
    }

    @PostMapping("/read-all")
    @PreAuthorize("hasAuthority('NOTIFICATION_READ')")
    @Operation(summary = "Mark all my notifications as read")
    public Map<String, Integer> markAllRead() {
        return Map.of("marked", notificationService.markAllRead());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('NOTIFICATION_READ')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete one of my notifications")
    public void delete(@PathVariable Long id) {
        notificationService.delete(id);
    }
}
