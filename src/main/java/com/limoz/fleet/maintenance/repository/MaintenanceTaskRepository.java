package com.limoz.fleet.maintenance.repository;

import com.limoz.fleet.maintenance.domain.MaintenanceTask;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MaintenanceTaskRepository extends JpaRepository<MaintenanceTask, Long> {

    long countByServiceTypeId(Long serviceTypeId);
}
