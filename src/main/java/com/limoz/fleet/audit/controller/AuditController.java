package com.limoz.fleet.audit.controller;

import com.limoz.fleet.audit.domain.AuditAction;
import com.limoz.fleet.audit.domain.AuditLog;
import com.limoz.fleet.audit.repository.AuditLogRepository;

import com.limoz.fleet.audit.dto.AuditLogResponse;
import com.limoz.fleet.common.api.PageResponse;
import com.limoz.fleet.common.util.Specifications;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1/audit-logs")
@RequiredArgsConstructor
@Tag(name = "Audit Log")
@PreAuthorize("hasAuthority('AUDIT_VIEW')")
public class AuditController {

    private final AuditLogRepository repository;

    @GetMapping
    @Transactional(readOnly = true)
    @Operation(summary = "Search the audit trail")
    public PageResponse<AuditLogResponse> search(@RequestParam(required = false) String q,
                                                 @RequestParam(required = false) AuditAction action,
                                                 @RequestParam(required = false) String entityType,
                                                 @RequestParam(required = false) Long entityId,
                                                 @RequestParam(required = false) Long userId,
                                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
                                                 @ParameterObject @PageableDefault(size = 25, sort = "occurredAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Specification<AuditLog> spec = Specifications.and(
                Specifications.likeAny(q, "description", "entityReference", "username"),
                Specifications.equal("action", action),
                Specifications.equal("entityType", entityType),
                Specifications.equal("entityId", entityId),
                Specifications.equal("userId", userId),
                Specifications.instantBetween("occurredAt", from, to));
        return PageResponse.from(repository.findAll(spec, pageable).map(AuditController::toResponse));
    }

    @GetMapping("/entity/{entityType}/{entityId}")
    @Transactional(readOnly = true)
    @Operation(summary = "Recent audit history of one record")
    public List<AuditLogResponse> forEntity(@PathVariable String entityType, @PathVariable Long entityId) {
        return repository.findTop50ByEntityTypeAndEntityIdOrderByOccurredAtDesc(entityType, entityId).stream()
                .map(AuditController::toResponse).toList();
    }

    public static AuditLogResponse toResponse(AuditLog a) {
        return new AuditLogResponse(a.getId(), a.getUserId(), a.getUsername(), a.getAction(), a.getEntityType(), a.getEntityId(),
                a.getEntityReference(), a.getDescription(), a.getPreviousValue(), a.getNewValue(), a.getIpAddress(), a.getOccurredAt());
    }
}
