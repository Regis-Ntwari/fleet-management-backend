package com.limoz.fleet.maintenance.dto;

import com.limoz.fleet.maintenance.domain.MaintenanceRecordStatus;
import com.limoz.fleet.maintenance.domain.MaintenanceType;
import com.limoz.fleet.maintenance.domain.PaymentStatus;
import com.limoz.fleet.maintenance.domain.Priority;
import com.limoz.fleet.maintenance.domain.WorkshopType;

import java.time.LocalDate;
import java.util.List;

/** Optional filters of the maintenance job list; {@code q} matches MNT / GRG number, plate or complaint. */
public record MaintenanceFilter(String q, Long vehicleId, List<MaintenanceRecordStatus> status, MaintenanceType type, Priority priority,
                                Long workshopId, WorkshopType workshopType, Long technicianUserId, LocalDate from, LocalDate to,
                                PaymentStatus paymentStatus, Boolean intakeOnly) {}
