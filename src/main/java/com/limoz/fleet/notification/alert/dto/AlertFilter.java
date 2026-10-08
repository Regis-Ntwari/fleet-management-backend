package com.limoz.fleet.notification.alert.dto;

import com.limoz.fleet.notification.domain.NotificationSeverity;
import com.limoz.fleet.notification.alert.domain.AlertStatus;
import com.limoz.fleet.notification.alert.domain.AlertType;

public record AlertFilter(AlertStatus status, NotificationSeverity severity, AlertType type, String entityType) {}
