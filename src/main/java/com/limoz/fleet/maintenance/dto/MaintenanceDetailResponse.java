package com.limoz.fleet.maintenance.dto;

import java.math.BigDecimal;
import java.util.List;

/** Job detail: the job itself plus its task checklist, part lines and timeline. */
public record MaintenanceDetailResponse(
        MaintenanceResponse job,
        List<MaintenanceTaskResponse> tasks,
        List<MaintenancePartResponse> parts,
        List<MaintenanceCommentResponse> comments,
        int tasksDone,
        int tasksTotal,
        int partsAwaitingApproval,
        BigDecimal approvedPartsTotal) {}
