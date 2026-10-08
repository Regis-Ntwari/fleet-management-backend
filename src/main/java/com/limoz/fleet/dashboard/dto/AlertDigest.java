package com.limoz.fleet.dashboard.dto;

import java.time.Instant;
import java.util.List;

public record AlertDigest(long active, long critical, long warning, long info, List<Item> items) {

    public record Item(Long id, String type, String severity, String title, String message, String entityType, Long entityId,
                       String entityReference, String linkPath, String status, Instant lastDetectedAt) {}
}
