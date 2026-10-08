package com.limoz.fleet.audit.repository;

import com.limoz.fleet.audit.domain.AuditLog;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long>, JpaSpecificationExecutor<AuditLog> {

    Page<AuditLog> findByEntityTypeAndEntityIdOrderByOccurredAtDesc(String entityType, Long entityId, Pageable pageable);

    List<AuditLog> findTop50ByEntityTypeAndEntityIdOrderByOccurredAtDesc(String entityType, Long entityId);
}
