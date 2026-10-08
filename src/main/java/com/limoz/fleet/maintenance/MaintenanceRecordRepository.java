package com.limoz.fleet.maintenance;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface MaintenanceRecordRepository extends JpaRepository<MaintenanceRecord, Long>, JpaSpecificationExecutor<MaintenanceRecord> {

    @EntityGraph(attributePaths = {"vehicle", "vehicle.category", "vehicle.currentDriver", "workshop", "driver", "customer"})
    Optional<MaintenanceRecord> findDetailedById(Long id);

    @Override
    @EntityGraph(attributePaths = {"vehicle", "vehicle.category", "workshop"})
    Page<MaintenanceRecord> findAll(Specification<MaintenanceRecord> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"vehicle", "vehicle.category", "workshop"})
    List<MaintenanceRecord> findByStatusNotInOrderByReportedAtAsc(Collection<MaintenanceRecordStatus> statuses);

    boolean existsByVehicleIdAndStatusInAndIdNot(Long vehicleId, Collection<MaintenanceRecordStatus> statuses, Long excludeId);

    @Query("select distinct m.vehicle.id from MaintenanceRecord m where m.status in :statuses")
    Set<Long> vehicleIdsWithStatusIn(@Param("statuses") Collection<MaintenanceRecordStatus> statuses);

    boolean existsByVehicleIdAndStatusIn(Long vehicleId, Collection<MaintenanceRecordStatus> statuses);

    long countByWorkshopId(Long workshopId);

    @Query("select m.status as status, count(m) as total from MaintenanceRecord m group by m.status")
    List<StatusCount> countByStatus();

    @Query("""
            select count(m) as total, coalesce(sum(m.totalCost), 0) as cost from MaintenanceRecord m
            where m.completedAt >= :from and m.completedAt < :to and m.status in :statuses
            """)
    CountAndCost completedBetween(@Param("from") Instant from, @Param("to") Instant to,
                                  @Param("statuses") Collection<MaintenanceRecordStatus> statuses);

    @Query("""
            select count(m) as total, coalesce(sum(m.totalCost), 0) as cost,
                   coalesce(sum(m.laborCost), 0) as laborCost, coalesce(sum(m.partsCost), 0) as partsCost,
                   coalesce(sum(m.otherCost), 0) as otherCost, coalesce(sum(m.amountPaid), 0) as amountPaid
            from MaintenanceRecord m
            where m.reportedAt >= :from and m.reportedAt < :to and m.status <> :excluded
            """)
    PeriodTotals totalsBetween(@Param("from") Instant from, @Param("to") Instant to, @Param("excluded") MaintenanceRecordStatus excluded);

    @Query("""
            select m.maintenanceType as type, count(m) as total, coalesce(sum(m.totalCost), 0) as cost
            from MaintenanceRecord m
            where m.reportedAt >= :from and m.reportedAt < :to and m.status <> :excluded
            group by m.maintenanceType order by sum(m.totalCost) desc
            """)
    List<TypeCost> costByType(@Param("from") Instant from, @Param("to") Instant to, @Param("excluded") MaintenanceRecordStatus excluded);

    @Query("""
            select m.vehicle.id as vehicleId, m.vehicle.plateNumber as plateNumber, m.vehicle.make as make, m.vehicle.model as model,
                   count(m) as total, coalesce(sum(m.totalCost), 0) as cost
            from MaintenanceRecord m
            where m.reportedAt >= :from and m.reportedAt < :to and m.status <> :excluded
            group by m.vehicle.id, m.vehicle.plateNumber, m.vehicle.make, m.vehicle.model
            order by sum(m.totalCost) desc
            """)
    List<VehicleCost> topVehiclesByCost(@Param("from") Instant from, @Param("to") Instant to,
                                        @Param("excluded") MaintenanceRecordStatus excluded, Pageable pageable);

    /** Average days between intake and release (or completion / now for jobs still open), computed by PostgreSQL. */
    @Query(value = """
            select coalesce(avg(extract(epoch from (coalesce(m.released_at, m.completed_at, now()) - m.reported_at)) / 86400.0), 0)
            from maintenance_records m
            where m.reported_at >= :from and m.reported_at < :to and m.status <> 'CANCELLED'
            """, nativeQuery = true)
    Number averageDaysInGarageBetween(@Param("from") Instant from, @Param("to") Instant to);

    /** Average days in garage of the jobs currently in the garage (not released or cancelled). */
    @Query(value = """
            select coalesce(avg(extract(epoch from (now() - m.reported_at)) / 86400.0), 0)
            from maintenance_records m
            where m.status not in ('RELEASED', 'CANCELLED')
            """, nativeQuery = true)
    Number averageDaysInGarageOpen();

    interface StatusCount {
        MaintenanceRecordStatus getStatus();
        long getTotal();
    }

    interface CountAndCost {
        long getTotal();
        BigDecimal getCost();
    }

    interface PeriodTotals extends CountAndCost {
        BigDecimal getLaborCost();
        BigDecimal getPartsCost();
        BigDecimal getOtherCost();
        BigDecimal getAmountPaid();
    }

    interface TypeCost extends CountAndCost {
        MaintenanceType getType();
    }

    interface VehicleCost extends CountAndCost {
        Long getVehicleId();
        String getPlateNumber();
        String getMake();
        String getModel();
    }
}
