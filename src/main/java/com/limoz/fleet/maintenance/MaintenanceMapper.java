package com.limoz.fleet.maintenance;

import com.limoz.fleet.common.util.DateRanges;
import com.limoz.fleet.maintenance.dto.MaintenanceCommentResponse;
import com.limoz.fleet.maintenance.dto.MaintenanceDetailResponse;
import com.limoz.fleet.maintenance.dto.MaintenancePartResponse;
import com.limoz.fleet.maintenance.dto.MaintenanceResponse;
import com.limoz.fleet.maintenance.dto.MaintenanceTaskResponse;
import com.limoz.fleet.maintenance.dto.ScheduleResponse;
import com.limoz.fleet.maintenance.dto.ServiceTypeResponse;
import com.limoz.fleet.maintenance.dto.WorkshopResponse;
import com.limoz.fleet.settings.SettingsService;
import com.limoz.fleet.vehicle.VehicleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

@Component
@RequiredArgsConstructor
public class MaintenanceMapper {

    private final VehicleMapper vehicleMapper;
    private final SettingsService settingsService;
    private final Clock clock;
    private final ZoneId operationalZone;

    public MaintenanceResponse toResponse(MaintenanceRecord m) {
        long days = daysInGarage(m);
        return new MaintenanceResponse(m.getId(), m.getMaintenanceNumber(), m.getIntakeNumber(), vehicleMapper.toSummary(m.getVehicle()),
                m.getReportedAt(), m.getReportedByUserId(),
                m.getCustomer() == null ? null : m.getCustomer().getId(), m.getCustomer() == null ? null : m.getCustomer().getName(),
                m.getOwnerName(), m.getDepartment(), m.getDriver() == null ? null : m.getDriver().getId(), m.getDriverName(), m.getDriverContact(),
                m.getComplaint(), m.getVisibleCondition(), m.getMaintenanceType(), m.getPriority(),
                m.getWorkshop() == null ? null : m.getWorkshop().getId(), m.getWorkshop() == null ? null : m.getWorkshop().getName(),
                m.getWorkshop() == null ? null : m.getWorkshop().getWorkshopType(),
                m.getTechnicianUserId(), m.getTechnicianName(), m.getManagerUserId(), m.getIncidentId(), m.getOdometerKm(),
                m.getStartedAt(), m.getExpectedCompletionAt(), m.getCompletedAt(), m.getReleasedAt(), m.getGatePassNumber(),
                m.getReviewDate(), m.getDiagnosis(), m.getObservedFaults(), m.getRecommendedRepair(), m.getLabourNotes(), m.getServicePerformed(),
                m.getLaborCost(), m.getPartsCost(), m.getOtherCost(), m.getTotalCost(), m.getAmountPaid(),
                m.getTotalCost().subtract(m.getAmountPaid()).max(BigDecimal.ZERO), m.getPaymentStatus(), m.getStatus(),
                m.getApprovedByUserId(), m.getApprovedAt(), m.getCancellationReason(), m.getComments(),
                days, band(days), m.getCreatedAt(), m.getUpdatedAt(), m.getCreatedBy(), m.getUpdatedBy());
    }

    public MaintenanceDetailResponse toDetail(MaintenanceRecord m) {
        int done = (int) m.getTasks().stream().filter(t -> t.getStatus() == TaskStatus.DONE).count();
        int awaiting = (int) m.getParts().stream().filter(p -> p.getStatus() == PartStatus.REQUESTED).count();
        BigDecimal approvedTotal = m.getParts().stream().filter(p -> p.getStatus().isCosted())
                .map(MaintenancePart::getLineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new MaintenanceDetailResponse(toResponse(m),
                m.getTasks().stream().map(this::toResponse).toList(),
                m.getParts().stream().map(this::toResponse).toList(),
                m.getCommentEntries().stream().map(this::toResponse).toList(),
                done, m.getTasks().size(), awaiting, approvedTotal);
    }

    public MaintenanceTaskResponse toResponse(MaintenanceTask t) {
        return new MaintenanceTaskResponse(t.getId(), t.getServiceType() == null ? null : t.getServiceType().getId(),
                t.getServiceType() == null ? null : t.getServiceType().getName(), t.getDescription(), t.getStatus(), t.getLaborHours(),
                t.getLaborCost(), t.getCompletedAt(), t.getCompletedBy(), t.getNotes(), t.getSortOrder());
    }

    public MaintenancePartResponse toResponse(MaintenancePart p) {
        return new MaintenancePartResponse(p.getId(), p.getSparePart() == null ? null : p.getSparePart().getId(), p.getPartName(),
                p.getPartNumber(), p.getQuantity(), p.getUnitCost(), p.getLineTotal(), p.getStatus(), p.getApprovedByUserId(),
                p.getApprovedAt(), p.getRejectionReason(), p.getStockMovementId(), p.getCreatedAt(), p.getCreatedBy());
    }

    public MaintenanceCommentResponse toResponse(MaintenanceComment c) {
        return new MaintenanceCommentResponse(c.getId(), c.getUserId(), c.getAuthorName(), c.getAuthorRole(), c.getCommentType(),
                c.getFromStatus(), c.getToStatus(), c.getBody(), c.getCreatedAt());
    }

    public WorkshopResponse toResponse(Workshop w) {
        return new WorkshopResponse(w.getId(), w.getName(), w.getWorkshopType(), w.getContactName(), w.getPhone(), w.getEmail(),
                w.getAddress(), w.isActive());
    }

    public ServiceTypeResponse toResponse(ServiceType s) {
        return new ServiceTypeResponse(s.getId(), s.getCode(), s.getName(), s.getCategory(), s.getDefaultIntervalKm(),
                s.getDefaultIntervalDays(), s.isActive(), s.getSortOrder());
    }

    public ScheduleResponse toResponse(MaintenanceSchedule s) {
        LocalDate today = LocalDate.now(clock);
        long odometer = s.getVehicle().getOdometerKm();
        return new ScheduleResponse(s.getId(), vehicleMapper.toSummary(s.getVehicle()), odometer, s.getServiceType().getId(),
                s.getServiceType().getCode(), s.getServiceType().getName(), s.getIntervalKm(), s.getIntervalDays(),
                s.getLastServiceOdometer(), s.getLastServiceDate(), s.getLastMaintenanceRecordId(), s.getNextServiceOdometer(),
                s.getNextServiceDate(), ScheduleCalculator.kmRemaining(odometer, s.getNextServiceOdometer()),
                ScheduleCalculator.daysRemaining(today, s.getNextServiceDate()), s.getStatus(), s.isActive(), s.getNotes(), s.getUpdatedAt());
    }

    /** Calendar days from intake to release (or to now while the vehicle is still in the garage). */
    public long daysInGarage(MaintenanceRecord m) {
        Instant end = m.getReleasedAt() != null ? m.getReleasedAt()
                : m.getStatus() == MaintenanceRecordStatus.CANCELLED && m.getUpdatedAt() != null ? m.getUpdatedAt()
                : Instant.now(clock);
        LocalDate start = DateRanges.toLocalDate(m.getReportedAt(), operationalZone);
        LocalDate stop = DateRanges.toLocalDate(end, operationalZone);
        return Math.max(0, ChronoUnit.DAYS.between(start, stop));
    }

    public GarageDayBand band(long days) {
        return GarageDayBand.of(days, settingsService.getInt(MaintenanceSettingKeys.GARAGE_AMBER_DAYS),
                settingsService.getInt(MaintenanceSettingKeys.GARAGE_RED_DAYS));
    }
}
