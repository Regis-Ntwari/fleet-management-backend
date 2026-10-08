package com.limoz.fleet.maintenance.repository;

import com.limoz.fleet.maintenance.domain.MaintenancePart;
import com.limoz.fleet.maintenance.domain.MaintenanceRecordStatus;
import com.limoz.fleet.maintenance.domain.PartStatus;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;

public interface MaintenancePartRepository extends JpaRepository<MaintenancePart, Long> {

    @Query("select count(p) from MaintenancePart p where p.status = :status and p.record.status in :recordStatuses")
    long countByStatusAndRecordStatusIn(@Param("status") PartStatus status,
                                        @Param("recordStatuses") Collection<MaintenanceRecordStatus> recordStatuses);
}
