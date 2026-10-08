package com.limoz.fleet.maintenance.controller;

import com.limoz.fleet.maintenance.service.MaintenanceService;

import com.limoz.fleet.common.api.PageResponse;
import com.limoz.fleet.maintenance.dto.GarageDashboardResponse;
import com.limoz.fleet.maintenance.dto.MaintenanceCancelRequest;
import com.limoz.fleet.maintenance.dto.MaintenanceCommentRequest;
import com.limoz.fleet.maintenance.dto.MaintenanceCompleteRequest;
import com.limoz.fleet.maintenance.dto.MaintenanceDetailResponse;
import com.limoz.fleet.maintenance.dto.MaintenanceFilter;
import com.limoz.fleet.maintenance.dto.MaintenanceNoteRequest;
import com.limoz.fleet.maintenance.dto.MaintenancePartRequest;
import com.limoz.fleet.maintenance.dto.MaintenancePaymentRequest;
import com.limoz.fleet.maintenance.dto.MaintenanceRequest;
import com.limoz.fleet.maintenance.dto.MaintenanceResponse;
import com.limoz.fleet.maintenance.dto.MaintenanceReviewRequest;
import com.limoz.fleet.maintenance.dto.MaintenanceSummaryResponse;
import com.limoz.fleet.maintenance.dto.MaintenanceTaskRequest;
import com.limoz.fleet.maintenance.dto.MaintenanceTaskStatusRequest;
import com.limoz.fleet.maintenance.dto.PartRejectRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Maintenance", description = "Maintenance jobs: MNT jobs and the garage intake -> review -> work progress -> gate pass flow")
public class MaintenanceController {

    private final MaintenanceService maintenanceService;

    // ---------------------------------------------------------------- queries

    @GetMapping("/maintenance")
    @PreAuthorize("hasAuthority('MAINTENANCE_READ')")
    @Operation(summary = "Search maintenance jobs",
            description = "Example: `?q=MNT-2026&status=IN_PROGRESS&status=WAITING_FOR_PARTS&workshopType=INTERNAL&from=2026-06-01&page=0&size=20`")
    public PageResponse<MaintenanceResponse> search(@ParameterObject MaintenanceFilter filter,
                                                    @ParameterObject @PageableDefault(size = 20, sort = "reportedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return maintenanceService.search(filter, pageable);
    }

    @GetMapping("/maintenance/garage/dashboard")
    @PreAuthorize("hasAuthority('MAINTENANCE_READ')")
    @Operation(summary = "Garage dashboard: counts per status, vehicles in garage with days-in-garage bands, parts awaiting approval, month totals")
    public GarageDashboardResponse garageDashboard() {
        return maintenanceService.garageDashboard();
    }

    @GetMapping("/maintenance/summary")
    @PreAuthorize("hasAuthority('MAINTENANCE_READ')")
    @Operation(summary = "Period summary: job count, cost by type, average days in garage, top vehicles by cost (default: last 30 days)")
    public MaintenanceSummaryResponse summary(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return maintenanceService.summary(from, to);
    }

    @GetMapping("/maintenance/{id}")
    @PreAuthorize("hasAuthority('MAINTENANCE_READ')")
    @Operation(summary = "Job detail with tasks, parts and timeline")
    public MaintenanceDetailResponse get(@PathVariable Long id) {
        return maintenanceService.get(id);
    }

    @GetMapping("/vehicles/{vehicleId}/maintenance")
    @PreAuthorize("hasAuthority('MAINTENANCE_READ')")
    @Operation(summary = "Maintenance history of a vehicle")
    public PageResponse<MaintenanceResponse> forVehicle(@PathVariable Long vehicleId,
                                                        @ParameterObject @PageableDefault(size = 20, sort = "reportedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return maintenanceService.forVehicle(vehicleId, pageable);
    }

    // ---------------------------------------------------------------- creation & edit

    @PostMapping("/maintenance")
    @PreAuthorize("hasAuthority('MAINTENANCE_MANAGE')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Log a maintenance job (MNT number) at status REPORTED")
    public MaintenanceDetailResponse report(@Valid @RequestBody MaintenanceRequest request) {
        return maintenanceService.report(request);
    }

    @PostMapping("/maintenance/intake")
    @PreAuthorize("hasAuthority('MAINTENANCE_MANAGE')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Garage reception check-in (MNT + GRG intake number) at status REPORTED")
    public MaintenanceDetailResponse intake(@Valid @RequestBody MaintenanceRequest request) {
        return maintenanceService.intake(request);
    }

    @PutMapping("/maintenance/{id}")
    @PreAuthorize("hasAuthority('MAINTENANCE_MANAGE')")
    @Operation(summary = "Edit job details while the job is open")
    public MaintenanceDetailResponse update(@PathVariable Long id, @Valid @RequestBody MaintenanceRequest request) {
        return maintenanceService.update(id, request);
    }

    // ---------------------------------------------------------------- lifecycle

    @PostMapping("/maintenance/{id}/review")
    @PreAuthorize("hasAuthority('MAINTENANCE_MANAGE')")
    @Operation(summary = "Mechanic review: diagnosis, faults, repair plan and parts needed (REPORTED -> INSPECTION)")
    public MaintenanceDetailResponse review(@PathVariable Long id, @Valid @RequestBody MaintenanceReviewRequest request) {
        return maintenanceService.submitReview(id, request);
    }

    @PostMapping("/maintenance/{id}/approve")
    @PreAuthorize("hasAuthority('MAINTENANCE_APPROVE')")
    @Operation(summary = "Approve the job (REPORTED / INSPECTION -> APPROVED)")
    public MaintenanceDetailResponse approve(@PathVariable Long id) {
        return maintenanceService.approve(id);
    }

    @PostMapping("/maintenance/{id}/start")
    @PreAuthorize("hasAuthority('MAINTENANCE_MANAGE')")
    @Operation(summary = "Start the work: vehicle goes IN_MAINTENANCE (refused while on a trip)")
    public MaintenanceDetailResponse start(@PathVariable Long id) {
        return maintenanceService.start(id);
    }

    @PostMapping("/maintenance/{id}/wait-for-parts")
    @PreAuthorize("hasAuthority('MAINTENANCE_MANAGE')")
    @Operation(summary = "Block the job while parts are sourced (IN_PROGRESS -> WAITING_FOR_PARTS)")
    public MaintenanceDetailResponse waitForParts(@PathVariable Long id, @Valid @RequestBody(required = false) MaintenanceNoteRequest request) {
        return maintenanceService.waitForParts(id, request == null ? null : request.note());
    }

    @PostMapping("/maintenance/{id}/resume")
    @PreAuthorize("hasAuthority('MAINTENANCE_MANAGE')")
    @Operation(summary = "Resume the work (WAITING_FOR_PARTS -> IN_PROGRESS)")
    public MaintenanceDetailResponse resume(@PathVariable Long id, @Valid @RequestBody(required = false) MaintenanceNoteRequest request) {
        return maintenanceService.resume(id, request == null ? null : request.note());
    }

    @PostMapping("/maintenance/{id}/complete")
    @PreAuthorize("hasAuthority('MAINTENANCE_MANAGE')")
    @Operation(summary = "Work-done form: computes costs, releases the vehicle from the workshop and resets preventive schedules")
    public MaintenanceDetailResponse complete(@PathVariable Long id, @Valid @RequestBody MaintenanceCompleteRequest request) {
        return maintenanceService.complete(id, request);
    }

    @PostMapping("/maintenance/{id}/release")
    @PreAuthorize("hasAuthority('MAINTENANCE_MANAGE')")
    @Operation(summary = "Issue the gate pass (COMPLETED -> RELEASED)")
    public MaintenanceDetailResponse release(@PathVariable Long id) {
        return maintenanceService.release(id);
    }

    @PostMapping("/maintenance/{id}/cancel")
    @PreAuthorize("hasAuthority('MAINTENANCE_MANAGE')")
    @Operation(summary = "Cancel a job that is not yet released")
    public MaintenanceDetailResponse cancel(@PathVariable Long id, @Valid @RequestBody MaintenanceCancelRequest request) {
        return maintenanceService.cancel(id, request);
    }

    // ---------------------------------------------------------------- parts

    @PostMapping("/maintenance/{id}/parts")
    @PreAuthorize("hasAuthority('MAINTENANCE_MANAGE')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Request a part (from the catalogue or free text)")
    public MaintenanceDetailResponse addPart(@PathVariable Long id, @Valid @RequestBody MaintenancePartRequest request) {
        return maintenanceService.addPart(id, request);
    }

    @PostMapping("/maintenance/{id}/parts/{partId}/approve")
    @PreAuthorize("hasAuthority('MAINTENANCE_APPROVE')")
    @Operation(summary = "Approve a requested part; catalogue parts are issued from stock (422 INSUFFICIENT_STOCK when short)")
    public MaintenanceDetailResponse approvePart(@PathVariable Long id, @PathVariable Long partId) {
        return maintenanceService.approvePart(id, partId);
    }

    @PostMapping("/maintenance/{id}/parts/{partId}/reject")
    @PreAuthorize("hasAuthority('MAINTENANCE_APPROVE')")
    @Operation(summary = "Reject a requested part")
    public MaintenanceDetailResponse rejectPart(@PathVariable Long id, @PathVariable Long partId, @Valid @RequestBody PartRejectRequest request) {
        return maintenanceService.rejectPart(id, partId, request);
    }

    @DeleteMapping("/maintenance/{id}/parts/{partId}")
    @PreAuthorize("hasAuthority('MAINTENANCE_MANAGE')")
    @Operation(summary = "Remove a part line that is still REQUESTED")
    public MaintenanceDetailResponse removePart(@PathVariable Long id, @PathVariable Long partId) {
        return maintenanceService.removePart(id, partId);
    }

    // ---------------------------------------------------------------- tasks & comments

    @PostMapping("/maintenance/{id}/tasks")
    @PreAuthorize("hasAuthority('MAINTENANCE_MANAGE')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a checklist task (optionally linked to a service type)")
    public MaintenanceDetailResponse addTask(@PathVariable Long id, @Valid @RequestBody MaintenanceTaskRequest request) {
        return maintenanceService.addTask(id, request);
    }

    @PatchMapping("/maintenance/{id}/tasks/{taskId}")
    @PreAuthorize("hasAuthority('MAINTENANCE_MANAGE')")
    @Operation(summary = "Mark a task PENDING / DONE / SKIPPED")
    public MaintenanceDetailResponse setTaskStatus(@PathVariable Long id, @PathVariable Long taskId, @Valid @RequestBody MaintenanceTaskStatusRequest request) {
        return maintenanceService.setTaskStatus(id, taskId, request);
    }

    @DeleteMapping("/maintenance/{id}/tasks/{taskId}")
    @PreAuthorize("hasAuthority('MAINTENANCE_MANAGE')")
    public MaintenanceDetailResponse removeTask(@PathVariable Long id, @PathVariable Long taskId) {
        return maintenanceService.removeTask(id, taskId);
    }

    @PostMapping("/maintenance/{id}/comments")
    @PreAuthorize("hasAuthority('MAINTENANCE_MANAGE')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Post a mechanic / manager comment on the job timeline")
    public MaintenanceDetailResponse addComment(@PathVariable Long id, @Valid @RequestBody MaintenanceCommentRequest request) {
        return maintenanceService.addComment(id, request);
    }

    // ---------------------------------------------------------------- payments

    @PostMapping("/maintenance/{id}/payments")
    @PreAuthorize("hasAnyAuthority('MAINTENANCE_MANAGE','FINANCE_MANAGE')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Record a payment against a completed job (UNPAID / PARTIAL / PAID)")
    public MaintenanceDetailResponse recordPayment(@PathVariable Long id, @Valid @RequestBody MaintenancePaymentRequest request) {
        return maintenanceService.recordPayment(id, request);
    }
}
