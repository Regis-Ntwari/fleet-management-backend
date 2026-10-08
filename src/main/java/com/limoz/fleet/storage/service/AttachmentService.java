package com.limoz.fleet.storage.service;

import com.limoz.fleet.storage.domain.Attachment;
import com.limoz.fleet.storage.domain.FileStorage;
import com.limoz.fleet.storage.repository.AttachmentRepository;

import com.limoz.fleet.audit.domain.AuditAction;
import com.limoz.fleet.audit.service.AuditService;
import com.limoz.fleet.common.exception.BusinessRuleException;
import com.limoz.fleet.common.exception.ResourceNotFoundException;
import com.limoz.fleet.storage.dto.AttachmentResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AttachmentService {

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "application/pdf", "image/jpeg", "image/png", "image/webp", "image/gif",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "application/msword", "text/csv", "text/plain");

    private final AttachmentRepository repository;
    private final FileStorage storage;
    private final AuditService auditService;

    @Transactional
    public AttachmentResponse upload(MultipartFile file, String ownerType, Long ownerId, String category) {
        if (file == null || file.isEmpty()) {
            throw new BusinessRuleException("EMPTY_FILE", "Uploaded file is empty");
        }
        String contentType = file.getContentType() == null ? "application/octet-stream" : file.getContentType();
        if (!ALLOWED_TYPES.contains(contentType)) {
            throw new BusinessRuleException("UNSUPPORTED_FILE_TYPE", "File type " + contentType + " is not allowed");
        }
        String originalName = file.getOriginalFilename() == null ? "upload" : file.getOriginalFilename();
        String key;
        try (InputStream in = file.getInputStream()) {
            key = storage.store(originalName, contentType, in, file.getSize());
        } catch (IOException e) {
            throw new BusinessRuleException("UPLOAD_FAILED", "Could not read uploaded file");
        }
        Attachment attachment = new Attachment();
        attachment.setFileName(originalName.length() > 255 ? originalName.substring(originalName.length() - 255) : originalName);
        attachment.setContentType(contentType);
        attachment.setSizeBytes(file.getSize());
        attachment.setStorageProvider(storage.providerCode());
        attachment.setStorageKey(key);
        attachment.setOwnerType(ownerType);
        attachment.setOwnerId(ownerId);
        attachment.setCategory(category);
        attachment = repository.save(attachment);
        auditService.record(AuditAction.CREATE, "Attachment", attachment.getId(), attachment.getFileName(), null, null,
                "File uploaded for " + ownerType + " " + ownerId);
        return toResponse(attachment);
    }

    @Transactional(readOnly = true)
    public Attachment get(Long id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Attachment", id));
    }

    @Transactional(readOnly = true)
    public List<AttachmentResponse> forOwner(String ownerType, Long ownerId) {
        return repository.findByOwnerTypeAndOwnerIdOrderByCreatedAtDesc(ownerType, ownerId).stream().map(this::toResponse).toList();
    }

    public InputStream open(Attachment attachment) {
        return storage.open(attachment.getStorageKey());
    }

    @Transactional
    public void delete(Long id) {
        Attachment attachment = get(id);
        repository.delete(attachment);
        storage.delete(attachment.getStorageKey());
        auditService.record(AuditAction.DELETE, "Attachment", id, attachment.getFileName(), null, null, "File deleted");
    }

    public AttachmentResponse toResponse(Attachment a) {
        return new AttachmentResponse(a.getId(), a.getFileName(), a.getContentType(), a.getSizeBytes(), a.getOwnerType(),
                a.getOwnerId(), a.getCategory(), a.getCreatedBy(), a.getCreatedAt(), "/api/v1/attachments/" + a.getId() + "/download");
    }
}
