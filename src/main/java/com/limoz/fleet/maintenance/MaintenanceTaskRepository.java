package com.limoz.fleet.maintenance;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MaintenanceTaskRepository extends JpaRepository<MaintenanceTask, Long> {

    long countByServiceTypeId(Long serviceTypeId);
}
