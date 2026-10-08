package com.limoz.fleet.storage;

import com.limoz.fleet.storage.dto.AttachmentResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/attachments")
@RequiredArgsConstructor
@Tag(name = "Attachments", description = "File uploads for documents, receipts, invoices and incident photos")
public class AttachmentController {

    private final AttachmentService attachmentService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('DOCUMENT_MANAGE','FUEL_MANAGE','MAINTENANCE_MANAGE','INCIDENT_MANAGE','FINANCE_MANAGE','CUSTOMER_MANAGE','VEHICLE_UPDATE','DRIVER_MANAGE')")
    @Operation(summary = "Upload a file and link it to a record (ownerType + ownerId)")
    public AttachmentResponse upload(@RequestPart("file") MultipartFile file,
                                     @RequestParam String ownerType,
                                     @RequestParam(required = false) Long ownerId,
                                     @RequestParam(required = false) String category) {
        return attachmentService.upload(file, ownerType, ownerId, category);
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "List files attached to a record")
    public List<AttachmentResponse> list(@RequestParam String ownerType, @RequestParam Long ownerId) {
        return attachmentService.forOwner(ownerType, ownerId);
    }

    @GetMapping("/{id}/download")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Download a file")
    public ResponseEntity<InputStreamResource> download(@PathVariable Long id) {
        Attachment attachment = attachmentService.get(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + attachment.getFileName().replace("\"", "") + "\"")
                .contentType(MediaType.parseMediaType(attachment.getContentType()))
                .contentLength(attachment.getSizeBytes())
                .body(new InputStreamResource(attachmentService.open(attachment)));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyAuthority('DOCUMENT_MANAGE','FUEL_MANAGE','MAINTENANCE_MANAGE','INCIDENT_MANAGE','FINANCE_MANAGE','CUSTOMER_MANAGE')")
    @Operation(summary = "Delete a file")
    public void delete(@PathVariable Long id) {
        attachmentService.delete(id);
    }
}
