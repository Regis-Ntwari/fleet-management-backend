package com.limoz.fleet.document;

import com.limoz.fleet.audit.AuditAction;
import com.limoz.fleet.audit.AuditService;
import com.limoz.fleet.common.api.PageResponse;
import com.limoz.fleet.common.exception.BusinessRuleException;
import com.limoz.fleet.common.exception.DuplicateResourceException;
import com.limoz.fleet.common.exception.ResourceNotFoundException;
import com.limoz.fleet.config.CacheConfig;
import com.limoz.fleet.document.dto.DocumentRequest;
import com.limoz.fleet.document.dto.DocumentResponse;
import com.limoz.fleet.document.dto.DocumentTypeRequest;
import com.limoz.fleet.document.dto.DocumentTypeResponse;
import com.limoz.fleet.driver.Driver;
import com.limoz.fleet.driver.DriverService;
import com.limoz.fleet.settings.SettingKeys;
import com.limoz.fleet.settings.SettingsService;
import com.limoz.fleet.vehicle.Vehicle;
import com.limoz.fleet.vehicle.VehicleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class DocumentService {

    private final DocumentTypeRepository typeRepository;
    private final VehicleDocumentRepository vehicleDocumentRepository;
    private final DriverDocumentRepository driverDocumentRepository;
    private final VehicleService vehicleService;
    private final DriverService driverService;
    private final SettingsService settingsService;
    private final AuditService auditService;
    private final Clock clock;

    // ---------------------------------------------------------------- document types

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheConfig.REFERENCE_DATA, key = "'documentTypes'")
    public List<DocumentTypeResponse> listTypes() {
        return typeRepository.findAllByOrderBySortOrderAscNameAsc().stream().map(this::toResponse).toList();
    }

    @CacheEvict(cacheNames = CacheConfig.REFERENCE_DATA, allEntries = true)
    public DocumentTypeResponse createType(DocumentTypeRequest request) {
        if (typeRepository.existsByCodeIgnoreCase(request.code())) {
            throw new DuplicateResourceException("Document type " + request.code() + " already exists");
        }
        DocumentType type = new DocumentType();
        applyType(type, request);
        type.setCode(request.code());
        DocumentTypeResponse response = toResponse(typeRepository.save(type));
        auditService.record(AuditAction.CREATE, "DocumentType", type.getId(), type.getCode(), null, response, "Document type created");
        return response;
    }

    @CacheEvict(cacheNames = CacheConfig.REFERENCE_DATA, allEntries = true)
    public DocumentTypeResponse updateType(Long id, DocumentTypeRequest request) {
        DocumentType type = typeRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Document type", id));
        DocumentTypeResponse before = toResponse(type);
        applyType(type, request);
        DocumentTypeResponse after = toResponse(typeRepository.save(type));
        auditService.record(AuditAction.UPDATE, "DocumentType", id, type.getCode(), before, after, "Document type updated");
        return after;
    }

    private void applyType(DocumentType t, DocumentTypeRequest r) {
        t.setName(r.name().trim());
        t.setAppliesTo(r.appliesTo());
        t.setRequiredForDispatch(r.requiredForDispatch());
        t.setWarningDays(r.warningDays());
        t.setActive(r.active() == null || r.active());
        t.setSortOrder(r.sortOrder() == null ? 0 : r.sortOrder());
    }

    // ---------------------------------------------------------------- vehicle documents

    @Transactional(readOnly = true)
    public List<DocumentResponse> vehicleDocuments(Long vehicleId) {
        vehicleService.load(vehicleId);
        return vehicleDocumentRepository.findByVehicleIdOrderBySupersededAscExpiryDateDesc(vehicleId).stream().map(this::toResponse).toList();
    }

    @CacheEvict(cacheNames = CacheConfig.DASHBOARD, allEntries = true)
    public DocumentResponse addVehicleDocument(Long vehicleId, DocumentRequest request) {
        Vehicle vehicle = vehicleService.loadActive(vehicleId);
        DocumentType type = loadType(request.documentTypeId(), DocumentAppliesTo.VEHICLE);
        validateDates(request);
        // a new document of the same type supersedes the current one (renewal) - history is retained
        vehicleDocumentRepository.findByVehicleIdAndSupersededFalse(vehicleId).stream()
                .filter(d -> d.getDocumentType().getId().equals(type.getId()))
                .forEach(d -> d.setSuperseded(true));
        VehicleDocument doc = new VehicleDocument();
        doc.setVehicle(vehicle);
        doc.setDocumentType(type);
        doc.setCost(request.cost());
        applyDocument(doc, request);
        DocumentResponse response = toResponse(vehicleDocumentRepository.save(doc));
        auditService.record(AuditAction.CREATE, "VehicleDocument", doc.getId(), vehicle.getPlateNumber(), null, response,
                type.getName() + " recorded for " + vehicle.getPlateNumber());
        return response;
    }

    @CacheEvict(cacheNames = CacheConfig.DASHBOARD, allEntries = true)
    public DocumentResponse updateVehicleDocument(Long id, DocumentRequest request) {
        VehicleDocument doc = vehicleDocumentRepository.findDetailedById(id).orElseThrow(() -> new ResourceNotFoundException("Vehicle document", id));
        validateDates(request);
        DocumentResponse before = toResponse(doc);
        doc.setDocumentType(loadType(request.documentTypeId(), DocumentAppliesTo.VEHICLE));
        doc.setCost(request.cost());
        applyDocument(doc, request);
        DocumentResponse after = toResponse(vehicleDocumentRepository.save(doc));
        auditService.record(AuditAction.UPDATE, "VehicleDocument", id, doc.getVehicle().getPlateNumber(), before, after, "Vehicle document updated");
        return after;
    }

    @CacheEvict(cacheNames = CacheConfig.DASHBOARD, allEntries = true)
    public void deleteVehicleDocument(Long id) {
        VehicleDocument doc = vehicleDocumentRepository.findDetailedById(id).orElseThrow(() -> new ResourceNotFoundException("Vehicle document", id));
        vehicleDocumentRepository.delete(doc);
        auditService.record(AuditAction.DELETE, "VehicleDocument", id, doc.getVehicle().getPlateNumber(), toResponse(doc), null, "Vehicle document deleted");
    }

    // ---------------------------------------------------------------- driver documents

    @Transactional(readOnly = true)
    public List<DocumentResponse> driverDocuments(Long driverId) {
        driverService.load(driverId);
        return driverDocumentRepository.findByDriverIdOrderBySupersededAscExpiryDateDesc(driverId).stream().map(this::toResponse).toList();
    }

    public DocumentResponse addDriverDocument(Long driverId, DocumentRequest request) {
        Driver driver = driverService.loadActive(driverId);
        DocumentType type = loadType(request.documentTypeId(), DocumentAppliesTo.DRIVER);
        validateDates(request);
        driverDocumentRepository.findByDriverIdOrderBySupersededAscExpiryDateDesc(driverId).stream()
                .filter(d -> !d.isSuperseded() && d.getDocumentType().getId().equals(type.getId()))
                .forEach(d -> d.setSuperseded(true));
        DriverDocument doc = new DriverDocument();
        doc.setDriver(driver);
        doc.setDocumentType(type);
        applyDocument(doc, request);
        // keep the driver's licence expiry in sync with the licence document
        if ("DRIVING_LICENCE".equalsIgnoreCase(type.getCode()) && request.expiryDate() != null) {
            driver.setLicenseExpiryDate(request.expiryDate());
            if (request.documentNumber() != null && !request.documentNumber().isBlank()) {
                driver.setLicenseNumber(request.documentNumber().trim().toUpperCase());
            }
        }
        DocumentResponse response = toResponse(driverDocumentRepository.save(doc));
        auditService.record(AuditAction.CREATE, "DriverDocument", doc.getId(), driver.getDriverCode(), null, response,
                type.getName() + " recorded for " + driver.getFullName());
        return response;
    }

    public DocumentResponse updateDriverDocument(Long id, DocumentRequest request) {
        DriverDocument doc = driverDocumentRepository.findDetailedById(id).orElseThrow(() -> new ResourceNotFoundException("Driver document", id));
        validateDates(request);
        DocumentResponse before = toResponse(doc);
        doc.setDocumentType(loadType(request.documentTypeId(), DocumentAppliesTo.DRIVER));
        applyDocument(doc, request);
        DocumentResponse after = toResponse(driverDocumentRepository.save(doc));
        auditService.record(AuditAction.UPDATE, "DriverDocument", id, doc.getDriver().getDriverCode(), before, after, "Driver document updated");
        return after;
    }

    public void deleteDriverDocument(Long id) {
        DriverDocument doc = driverDocumentRepository.findDetailedById(id).orElseThrow(() -> new ResourceNotFoundException("Driver document", id));
        driverDocumentRepository.delete(doc);
        auditService.record(AuditAction.DELETE, "DriverDocument", id, doc.getDriver().getDriverCode(), toResponse(doc), null, "Driver document deleted");
    }

    // ---------------------------------------------------------------- queries used by dispatch, alerts and reports

    @Transactional(readOnly = true)
    public PageResponse<DocumentResponse> vehicleDocumentsByStatus(List<DocumentStatus> statuses, Pageable pageable) {
        List<DocumentStatus> s = statuses == null || statuses.isEmpty() ? List.of(DocumentStatus.EXPIRED, DocumentStatus.EXPIRING_SOON) : statuses;
        return PageResponse.from(vehicleDocumentRepository.pageActiveByStatusIn(s, pageable).map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public List<DocumentResponse> driverDocumentsByStatus(List<DocumentStatus> statuses) {
        List<DocumentStatus> s = statuses == null || statuses.isEmpty() ? List.of(DocumentStatus.EXPIRED, DocumentStatus.EXPIRING_SOON) : statuses;
        return driverDocumentRepository.findActiveByStatusIn(s).stream().map(this::toResponse).toList();
    }

    /**
     * Names of required document types that are missing or expired for the vehicle on the given date.
     * Empty list = vehicle is compliant for dispatch.
     */
    @Transactional(readOnly = true)
    public List<String> missingOrExpiredRequiredDocuments(Long vehicleId, LocalDate onDate) {
        List<DocumentType> required = typeRepository.findByRequiredForDispatchTrueAndActiveTrueAndAppliesToIn(
                List.of(DocumentAppliesTo.VEHICLE, DocumentAppliesTo.BOTH));
        if (required.isEmpty()) return List.of();
        List<VehicleDocument> current = vehicleDocumentRepository.findByVehicleIdAndSupersededFalse(vehicleId);
        List<String> problems = new ArrayList<>();
        for (DocumentType type : required) {
            VehicleDocument doc = current.stream().filter(d -> d.getDocumentType().getId().equals(type.getId())).findFirst().orElse(null);
            if (doc == null) {
                problems.add(type.getName() + " missing");
            } else if (doc.getExpiryDate() != null && doc.getExpiryDate().isBefore(onDate)) {
                problems.add(type.getName() + " expired on " + doc.getExpiryDate());
            }
        }
        return problems;
    }

    /** Recomputes VALID / EXPIRING_SOON / EXPIRED on every current document. Returns the number of status changes. */
    @CacheEvict(cacheNames = CacheConfig.DASHBOARD, allEntries = true)
    public int refreshStatuses() {
        LocalDate today = LocalDate.now(clock);
        int defaultWarning = settingsService.getInt(SettingKeys.DOCUMENT_EXPIRY_WARNING_DAYS);
        int changed = 0;
        for (VehicleDocument d : vehicleDocumentRepository.findBySupersededFalse()) {
            DocumentStatus status = DocumentStatusCalculator.compute(d.getExpiryDate(), today, warningDays(d.getDocumentType(), defaultWarning));
            if (status != d.getStatus()) {
                d.setStatus(status);
                changed++;
            }
        }
        for (DriverDocument d : driverDocumentRepository.findBySupersededFalse()) {
            DocumentStatus status = DocumentStatusCalculator.compute(d.getExpiryDate(), today, warningDays(d.getDocumentType(), defaultWarning));
            if (status != d.getStatus()) {
                d.setStatus(status);
                changed++;
            }
        }
        if (changed > 0) {
            log.info("Document status refresh: {} document(s) changed status", changed);
        }
        return changed;
    }

    // ---------------------------------------------------------------- helpers

    private DocumentType loadType(Long id, DocumentAppliesTo expected) {
        DocumentType type = typeRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Document type", id));
        if (type.getAppliesTo() != DocumentAppliesTo.BOTH && type.getAppliesTo() != expected) {
            throw new BusinessRuleException("Document type " + type.getName() + " does not apply to a " + expected.name().toLowerCase());
        }
        return type;
    }

    private void validateDates(DocumentRequest r) {
        if (r.issueDate() != null && r.expiryDate() != null && r.issueDate().isAfter(r.expiryDate())) {
            throw new BusinessRuleException("INVALID_DOCUMENT_DATES", "Issue date cannot be after expiry date");
        }
    }

    private void applyDocument(AbstractDocument d, DocumentRequest r) {
        d.setDocumentNumber(r.documentNumber());
        d.setIssuer(r.issuer());
        d.setIssueDate(r.issueDate());
        d.setExpiryDate(r.expiryDate());
        d.setAttachmentId(r.attachmentId());
        d.setNotes(r.notes());
        int warning = warningDays(d.getDocumentType(), settingsService.getInt(SettingKeys.DOCUMENT_EXPIRY_WARNING_DAYS));
        d.setStatus(DocumentStatusCalculator.compute(r.expiryDate(), LocalDate.now(clock), warning));
    }

    private int warningDays(DocumentType type, int defaultWarning) {
        return type.getWarningDays() != null ? type.getWarningDays() : defaultWarning;
    }

    public DocumentTypeResponse toResponse(DocumentType t) {
        return new DocumentTypeResponse(t.getId(), t.getCode(), t.getName(), t.getAppliesTo(), t.isRequiredForDispatch(),
                t.getWarningDays(), t.isActive(), t.getSortOrder());
    }

    public DocumentResponse toResponse(AbstractDocument d) {
        LocalDate today = LocalDate.now(clock);
        Long days = d.getExpiryDate() == null ? null : ChronoUnit.DAYS.between(today, d.getExpiryDate());
        String ownerType = d instanceof VehicleDocument ? "VEHICLE" : "DRIVER";
        java.math.BigDecimal cost = d instanceof VehicleDocument vd ? vd.getCost() : null;
        DocumentType t = d.getDocumentType();
        return new DocumentResponse(d.getId(), ownerType, d.ownerId(), d.ownerReference(), t.getId(), t.getCode(), t.getName(),
                t.isRequiredForDispatch(), d.getDocumentNumber(), d.getIssuer(), d.getIssueDate(), d.getExpiryDate(), days,
                d.getStatus(), d.getAttachmentId(), cost, d.getNotes(), d.isSuperseded(), d.getCreatedAt(), d.getUpdatedAt());
    }

    public Set<Long> vehicleIdsWithExpiredRequiredDocuments() {
        return vehicleDocumentRepository.findActiveByStatusIn(List.of(DocumentStatus.EXPIRED)).stream()
                .filter(d -> d.getDocumentType().isRequiredForDispatch())
                .map(d -> d.getVehicle().getId()).collect(Collectors.toSet());
    }
}
