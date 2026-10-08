package com.limoz.fleet.storage.dto;

import java.time.Instant;

public record AttachmentResponse(Long id, String fileName, String contentType, long sizeBytes, String ownerType,
                                 Long ownerId, String category, String uploadedBy, Instant uploadedAt, String downloadUrl) {}
