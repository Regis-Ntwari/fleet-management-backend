package com.limoz.fleet.maintenance.dto;

import com.limoz.fleet.maintenance.CommentType;
import com.limoz.fleet.maintenance.MaintenanceRecordStatus;

import java.time.Instant;

public record MaintenanceCommentResponse(Long id, Long userId, String authorName, String authorRole, CommentType commentType,
                                         MaintenanceRecordStatus fromStatus, MaintenanceRecordStatus toStatus, String body,
                                         Instant createdAt) {}
