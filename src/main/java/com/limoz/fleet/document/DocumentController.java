package com.limoz.fleet.document;

import com.limoz.fleet.common.api.PageResponse;
import com.limoz.fleet.document.dto.DocumentRequest;
import com.limoz.fleet.document.dto.DocumentResponse;
import com.limoz.fleet.document.dto.DocumentTypeRequest;
import com.limoz.fleet.document.dto.DocumentTypeResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Documents & Certificates", description = "Vehicle and driver documents with automatic VALID / EXPIRING_SOON / EXPIRED status")
public class DocumentController {

    private final DocumentService documentService;

    @GetMapping("/document-types")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "List document types")
    public List<DocumentTypeResponse> types() {
        return documentService.listTypes();
    }

    @PostMapping("/document-types")
    @PreAuthorize("hasAnyAuthority('SETTINGS_MANAGE','DOCUMENT_MANAGE')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a document type")
    public DocumentTypeResponse createType(@Valid @RequestBody DocumentTypeRequest request) {
        return documentService.createType(request);
    }

    @PutMapping("/document-types/{id}")
    @PreAuthorize("hasAnyAuthority('SETTINGS_MANAGE','DOCUMENT_MANAGE')")
    @Operation(summary = "Update a document type")
    public DocumentTypeResponse updateType(@PathVariable Long id, @Valid @RequestBody DocumentTypeRequest request) {
        return documentService.updateType(id, request);
    }

    @GetMapping("/documents/vehicles")
    @PreAuthorize("hasAuthority('DOCUMENT_READ')")
    @Operation(summary = "Vehicle documents by status (default: expired + expiring soon) - the Certificates screen")
    public PageResponse<DocumentResponse> vehicleDocumentsByStatus(@RequestParam(required = false) List<DocumentStatus> status,
                                                                   @ParameterObject @PageableDefault(size = 20, sort = "expiryDate") Pageable pageable) {
        return documentService.vehicleDocumentsByStatus(status, pageable);
    }

    @GetMapping("/documents/drivers")
    @PreAuthorize("hasAuthority('DOCUMENT_READ')")
    @Operation(summary = "Driver documents by status (default: expired + expiring soon)")
    public List<DocumentResponse> driverDocumentsByStatus(@RequestParam(required = false) List<DocumentStatus> status) {
        return documentService.driverDocumentsByStatus(status);
    }

    @GetMapping("/vehicles/{vehicleId}/documents")
    @PreAuthorize("hasAuthority('DOCUMENT_READ')")
    @Operation(summary = "Documents of a vehicle (current and superseded)")
    public List<DocumentResponse> vehicleDocuments(@PathVariable Long vehicleId) {
        return documentService.vehicleDocuments(vehicleId);
    }

    @PostMapping("/vehicles/{vehicleId}/documents")
    @PreAuthorize("hasAuthority('DOCUMENT_MANAGE')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add / renew a vehicle document (previous document of the same type is superseded)")
    public DocumentResponse addVehicleDocument(@PathVariable Long vehicleId, @Valid @RequestBody DocumentRequest request) {
        return documentService.addVehicleDocument(vehicleId, request);
    }

    @PutMapping("/vehicles/documents/{id}")
    @PreAuthorize("hasAuthority('DOCUMENT_MANAGE')")
    public DocumentResponse updateVehicleDocument(@PathVariable Long id, @Valid @RequestBody DocumentRequest request) {
        return documentService.updateVehicleDocument(id, request);
    }

    @DeleteMapping("/vehicles/documents/{id}")
    @PreAuthorize("hasAuthority('DOCUMENT_MANAGE')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteVehicleDocument(@PathVariable Long id) {
        documentService.deleteVehicleDocument(id);
    }

    @GetMapping("/drivers/{driverId}/documents")
    @PreAuthorize("hasAuthority('DOCUMENT_READ')")
    public List<DocumentResponse> driverDocuments(@PathVariable Long driverId) {
        return documentService.driverDocuments(driverId);
    }

    @PostMapping("/drivers/{driverId}/documents")
    @PreAuthorize("hasAuthority('DOCUMENT_MANAGE')")
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentResponse addDriverDocument(@PathVariable Long driverId, @Valid @RequestBody DocumentRequest request) {
        return documentService.addDriverDocument(driverId, request);
    }

    @PutMapping("/drivers/documents/{id}")
    @PreAuthorize("hasAuthority('DOCUMENT_MANAGE')")
    public DocumentResponse updateDriverDocument(@PathVariable Long id, @Valid @RequestBody DocumentRequest request) {
        return documentService.updateDriverDocument(id, request);
    }

    @DeleteMapping("/drivers/documents/{id}")
    @PreAuthorize("hasAuthority('DOCUMENT_MANAGE')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteDriverDocument(@PathVariable Long id) {
        documentService.deleteDriverDocument(id);
    }

    @PostMapping("/documents/refresh-statuses")
    @PreAuthorize("hasAuthority('DOCUMENT_MANAGE')")
    @Operation(summary = "Recompute all document statuses now (also runs nightly)")
    public java.util.Map<String, Integer> refresh() {
        return java.util.Map.of("changed", documentService.refreshStatuses());
    }
}
