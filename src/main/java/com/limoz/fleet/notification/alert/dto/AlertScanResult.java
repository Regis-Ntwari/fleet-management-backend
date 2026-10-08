package com.limoz.fleet.notification.alert.dto;

import java.time.Instant;
import java.util.List;

/**
 * Outcome of one scan. {@code failedScanners} lists scanners that threw; when any scanner fails, auto-resolution is
 * skipped for that run so alerts of the failing module are not cleared by mistake.
 */
public record AlertScanResult(Instant scannedAt, int scanners, int candidates, int created, int reraised, int refreshed,
                              int resolved, int notifications, List<String> failedScanners) {}
