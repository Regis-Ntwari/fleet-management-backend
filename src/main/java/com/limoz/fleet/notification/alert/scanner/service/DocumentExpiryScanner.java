package com.limoz.fleet.notification.alert.scanner.service;

import com.limoz.fleet.document.domain.AbstractDocument;
import com.limoz.fleet.document.domain.DocumentStatus;
import com.limoz.fleet.document.domain.DriverDocument;
import com.limoz.fleet.document.repository.DriverDocumentRepository;
import com.limoz.fleet.document.domain.VehicleDocument;
import com.limoz.fleet.document.repository.VehicleDocumentRepository;
import com.limoz.fleet.notification.domain.NotificationSeverity;
import com.limoz.fleet.notification.alert.domain.AlertCandidate;
import com.limoz.fleet.notification.alert.service.AlertScanner;
import com.limoz.fleet.notification.alert.domain.AlertType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Expired / expiring vehicle and driver documents. An expired document that is required for dispatch is CRITICAL
 * (the vehicle cannot legally be deployed); other expired and all expiring documents are WARNING. The driving
 * licence document is left to {@link DriverLicenceScanner}, which watches the driver's licence expiry directly.
 */
@Component
@RequiredArgsConstructor
public class DocumentExpiryScanner implements AlertScanner {

    static final String DRIVING_LICENCE_CODE = "DRIVING_LICENCE";
    private static final List<DocumentStatus> WATCHED = List.of(DocumentStatus.EXPIRED, DocumentStatus.EXPIRING_SOON);

    private final VehicleDocumentRepository vehicleDocumentRepository;
    private final DriverDocumentRepository driverDocumentRepository;

    @Override
    public List<AlertCandidate> scan() {
        List<AlertCandidate> out = new ArrayList<>();
        for (VehicleDocument d : vehicleDocumentRepository.findActiveByStatusIn(WATCHED)) {
            out.add(candidate(d, "VehicleDocument", d.getVehicle().getPlateNumber(), "/vehicles/" + d.getVehicle().getId() + "/documents"));
        }
        for (DriverDocument d : driverDocumentRepository.findActiveByStatusIn(WATCHED)) {
            if (DRIVING_LICENCE_CODE.equalsIgnoreCase(d.getDocumentType().getCode())) {
                continue;
            }
            out.add(candidate(d, "DriverDocument", d.getDriver().getFullName(), "/drivers/" + d.getDriver().getId() + "/documents"));
        }
        return out;
    }

    private static AlertCandidate candidate(AbstractDocument d, String entityType, String ownerReference, String link) {
        boolean expired = d.getStatus() == DocumentStatus.EXPIRED;
        boolean required = d.getDocumentType().isRequiredForDispatch();
        AlertType type = expired ? AlertType.DOCUMENT_EXPIRED : AlertType.DOCUMENT_EXPIRING;
        NotificationSeverity severity = expired && required ? NotificationSeverity.CRITICAL : NotificationSeverity.WARNING;
        String typeName = d.getDocumentType().getName();
        String title = typeName + (expired ? " expired: " : " expiring: ") + ownerReference;
        String message = typeName + " of " + ownerReference + (expired ? " expired on " : " expires on ") + d.getExpiryDate()
                + (required ? " (required for dispatch)" : "")
                + (d.getDocumentNumber() == null ? "" : ", document " + d.getDocumentNumber());
        return new AlertCandidate(type, severity, title, message, entityType, d.getId(), ownerReference + " - " + typeName, link,
                AlertCandidate.key(type, entityType, d.getId(), null));
    }
}
