package com.limoz.fleet.search.service;

import com.limoz.fleet.common.util.Specifications;
import com.limoz.fleet.finance.domain.Invoice;
import com.limoz.fleet.finance.repository.InvoiceRepository;
import com.limoz.fleet.incident.domain.Incident;
import com.limoz.fleet.incident.domain.TrafficFine;
import com.limoz.fleet.incident.repository.IncidentRepository;
import com.limoz.fleet.incident.repository.TrafficFineRepository;
import com.limoz.fleet.maintenance.domain.MaintenanceRecord;
import com.limoz.fleet.maintenance.repository.MaintenanceRecordRepository;
import com.limoz.fleet.search.domain.SearchSource;
import com.limoz.fleet.search.dto.SearchResult;
import com.limoz.fleet.security.Permissions;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;

/** Global search over maintenance jobs, incidents, traffic fines and invoices. */
@Configuration
@RequiredArgsConstructor
public class WorkshopComplianceSearchSources {

    private final MaintenanceRecordRepository maintenanceRepository;
    private final IncidentRepository incidentRepository;
    private final TrafficFineRepository fineRepository;
    private final InvoiceRepository invoiceRepository;

    @Bean
    SearchSource maintenanceSearchSource() {
        return new SearchSource() {
            public String group() { return "maintenance"; }
            public String requiredPermission() { return Permissions.MAINTENANCE_READ; }
            public List<SearchResult> search(String q, int limit) {
                Specification<MaintenanceRecord> spec = Specifications.likeAny(q, "maintenanceNumber", "intakeNumber", "vehicle.plateNumber", "complaint");
                return maintenanceRepository.findAll(spec, PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "reportedAt"))).stream()
                        .map(m -> new SearchResult("MAINTENANCE", m.getId(), m.getMaintenanceNumber(),
                                m.getMaintenanceNumber() + (m.getIntakeNumber() == null ? "" : " · " + m.getIntakeNumber()) + " · " + m.getVehicle().getPlateNumber(),
                                m.getComplaint().length() > 80 ? m.getComplaint().substring(0, 80) + "…" : m.getComplaint(),
                                m.getStatus().name(), "/maintenance/" + m.getId()))
                        .toList();
            }
        };
    }

    @Bean
    SearchSource incidentSearchSource() {
        return new SearchSource() {
            public String group() { return "incidents"; }
            public String requiredPermission() { return Permissions.INCIDENT_READ; }
            public List<SearchResult> search(String q, int limit) {
                Specification<Incident> spec = Specifications.likeAny(q, "incidentNumber", "vehicle.plateNumber", "location", "description");
                return incidentRepository.findAll(spec, PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "occurredAt"))).stream()
                        .map(i -> new SearchResult("INCIDENT", i.getId(), i.getIncidentNumber(),
                                i.getIncidentNumber() + " · " + i.getVehicle().getPlateNumber() + " · " + i.getIncidentType().name().replace('_', ' '),
                                (i.getLocation() == null ? "" : i.getLocation() + " · ") + i.getSeverity().name(), i.getStatus().name(), "/incidents/" + i.getId()))
                        .toList();
            }
        };
    }

    @Bean
    SearchSource fineSearchSource() {
        return new SearchSource() {
            public String group() { return "fines"; }
            public String requiredPermission() { return Permissions.INCIDENT_READ; }
            public List<SearchResult> search(String q, int limit) {
                Specification<TrafficFine> spec = Specifications.likeAny(q, "fineNumber", "ticketReference", "vehicle.plateNumber", "offence");
                return fineRepository.findAll(spec, PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "issuedAt"))).stream()
                        .map(f -> new SearchResult("TRAFFIC_FINE", f.getId(), f.getFineNumber(), f.getFineNumber() + " · " + f.getVehicle().getPlateNumber(),
                                f.getOffence() + " · " + f.getAmount().stripTrailingZeros().toPlainString() + " RWF", f.getStatus().name(), "/fines/" + f.getId()))
                        .toList();
            }
        };
    }

    @Bean
    SearchSource invoiceSearchSource() {
        return new SearchSource() {
            public String group() { return "invoices"; }
            public String requiredPermission() { return Permissions.FINANCE_READ; }
            public List<SearchResult> search(String q, int limit) {
                Specification<Invoice> spec = Specifications.likeAny(q, "invoiceNumber", "customer.name");
                return invoiceRepository.findAll(spec, PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "issueDate"))).stream()
                        .map(i -> new SearchResult("INVOICE", i.getId(), i.getInvoiceNumber(), i.getInvoiceNumber() + " · " + i.getCustomer().getName(),
                                i.getTotalAmount().stripTrailingZeros().toPlainString() + " " + i.getCurrency() + " · due " + i.getDueDate(),
                                i.getStatus().name(), "/invoices/" + i.getId()))
                        .toList();
            }
        };
    }
}
