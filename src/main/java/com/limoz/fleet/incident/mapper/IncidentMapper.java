package com.limoz.fleet.incident.mapper;

import com.limoz.fleet.incident.domain.Incident;
import com.limoz.fleet.incident.domain.IncidentUpdate;
import com.limoz.fleet.incident.domain.TrafficFine;

import com.limoz.fleet.driver.mapper.DriverMapper;
import com.limoz.fleet.incident.dto.IncidentResponse;
import com.limoz.fleet.incident.dto.IncidentUpdateResponse;
import com.limoz.fleet.incident.dto.TrafficFineResponse;
import com.limoz.fleet.vehicle.mapper.VehicleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

@Component
@RequiredArgsConstructor
public class IncidentMapper {

    private final VehicleMapper vehicleMapper;
    private final DriverMapper driverMapper;
    private final Clock clock;

    public IncidentResponse toResponse(Incident i, List<IncidentUpdate> updates) {
        return new IncidentResponse(i.getId(), i.getIncidentNumber(), vehicleMapper.toSummary(i.getVehicle()), driverMapper.toSummary(i.getDriver()),
                i.getTripId(), i.getOccurredAt(), i.getLocation(), i.getLatitude(), i.getLongitude(), i.getIncidentType(), i.getSeverity(),
                i.getDescription(), i.isThirdPartyInvolved(), i.isInjuries(), i.getPoliceReportNumber(), i.getFuelLossLitres(),
                i.getEstimatedCost(), i.getInvestigationNotes(), i.getCorrectiveAction(), i.getStatus(), i.getReportedByUserId(),
                i.getResolvedAt(), i.getClosedAt(), updates == null ? null : updates.stream().map(this::toResponse).toList(),
                i.getCreatedAt(), i.getUpdatedAt(), i.getCreatedBy());
    }

    public IncidentResponse toResponse(Incident i) {
        return toResponse(i, null);
    }

    public IncidentUpdateResponse toResponse(IncidentUpdate u) {
        return new IncidentUpdateResponse(u.getId(), u.getUpdateType(), u.getFromStatus(), u.getToStatus(), u.getNote(), u.getUserId(),
                u.getAuthorName(), u.getCreatedAt());
    }

    public TrafficFineResponse toResponse(TrafficFine f) {
        boolean overdue = f.getStatus().isOutstanding() && f.getDueDate() != null && f.getDueDate().isBefore(LocalDate.now(clock));
        return new TrafficFineResponse(f.getId(), f.getFineNumber(), f.getTicketReference(), vehicleMapper.toSummary(f.getVehicle()),
                driverMapper.toSummary(f.getDriver()), f.getTripId(), f.getIssuedAt(), f.getLocation(), f.getOffence(), f.getAmount(),
                f.getCurrency(), f.getDueDate(), f.getStatus(), overdue, f.getPaidAt(), f.getPaymentId(), f.isChargedToDriver(),
                f.getAttachmentId(), f.getNotes(), f.getCreatedAt(), f.getUpdatedAt(), f.getCreatedBy());
    }
}
