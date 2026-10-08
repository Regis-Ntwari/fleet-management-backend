package com.limoz.fleet.fuel.repository;

import com.limoz.fleet.fuel.domain.FuelTransaction;

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
import java.util.List;
import java.util.Optional;

public interface FuelTransactionRepository extends JpaRepository<FuelTransaction, Long>, JpaSpecificationExecutor<FuelTransaction> {

    @EntityGraph(attributePaths = {"vehicle", "vehicle.category", "driver"})
    Optional<FuelTransaction> findDetailedById(Long id);

    @Override
    @EntityGraph(attributePaths = {"vehicle", "vehicle.category", "driver"})
    Page<FuelTransaction> findAll(Specification<FuelTransaction> spec, Pageable pageable);

    /** Chronologically previous non-archived transaction of the vehicle (ties broken by id). */
    @Query("""
            select f from FuelTransaction f
            where f.vehicle.id = :vehicleId and f.archived = false and f.transactionAt < :at
              and (:excludeId is null or f.id <> :excludeId)
            order by f.transactionAt desc, f.id desc
            """)
    List<FuelTransaction> findPreviousBefore(@Param("vehicleId") Long vehicleId, @Param("at") Instant at,
                                             @Param("excludeId") Long excludeId, Pageable pageable);

    /** Chronologically next non-archived transaction of the vehicle after the given moment. */
    @Query("""
            select f from FuelTransaction f
            where f.vehicle.id = :vehicleId and f.archived = false and f.transactionAt > :at
              and (:excludeId is null or f.id <> :excludeId)
            order by f.transactionAt asc, f.id asc
            """)
    List<FuelTransaction> findNextAfter(@Param("vehicleId") Long vehicleId, @Param("at") Instant at,
                                        @Param("excludeId") Long excludeId, Pageable pageable);

    @Query("""
            select count(f) > 0 from FuelTransaction f
            where f.vehicle.id = :vehicleId and f.archived = false and upper(f.receiptNumber) = upper(:receipt)
              and (:excludeId is null or f.id <> :excludeId)
            """)
    boolean receiptExists(@Param("vehicleId") Long vehicleId, @Param("receipt") String receipt, @Param("excludeId") Long excludeId);

    @Query("""
            select count(f) as transactionCount,
                   coalesce(sum(f.litres), 0) as totalLitres,
                   coalesce(sum(f.totalAmount), 0) as totalCost,
                   coalesce(sum(f.distanceSinceLastKm), 0) as totalDistanceKm,
                   coalesce(sum(case when f.distanceSinceLastKm is not null then f.litres else 0 end), 0) as litresWithDistance,
                   coalesce(sum(case when f.anomaly = true then 1 else 0 end), 0) as anomalyCount
            from FuelTransaction f
            where f.archived = false and f.transactionAt >= :from and f.transactionAt < :to
              and (:vehicleId is null or f.vehicle.id = :vehicleId)
              and (:categoryId is null or f.vehicle.category.id = :categoryId)
            """)
    Totals totals(@Param("from") Instant from, @Param("to") Instant to,
                  @Param("vehicleId") Long vehicleId, @Param("categoryId") Long categoryId);

    @Query("""
            select f.vehicle.id as vehicleId, f.vehicle.plateNumber as plateNumber, f.vehicle.make as make, f.vehicle.model as model,
                   count(f) as transactionCount,
                   coalesce(sum(f.litres), 0) as totalLitres,
                   coalesce(sum(f.totalAmount), 0) as totalCost,
                   coalesce(sum(f.distanceSinceLastKm), 0) as totalDistanceKm,
                   coalesce(sum(case when f.distanceSinceLastKm is not null then f.litres else 0 end), 0) as litresWithDistance,
                   coalesce(sum(case when f.anomaly = true then 1 else 0 end), 0) as anomalyCount
            from FuelTransaction f
            where f.archived = false and f.transactionAt >= :from and f.transactionAt < :to
              and (:vehicleId is null or f.vehicle.id = :vehicleId)
              and (:categoryId is null or f.vehicle.category.id = :categoryId)
            group by f.vehicle.id, f.vehicle.plateNumber, f.vehicle.make, f.vehicle.model
            order by sum(f.totalAmount) desc
            """)
    List<VehicleTotals> totalsPerVehicle(@Param("from") Instant from, @Param("to") Instant to,
                                         @Param("vehicleId") Long vehicleId, @Param("categoryId") Long categoryId);

    interface Totals {
        long getTransactionCount();
        BigDecimal getTotalLitres();
        BigDecimal getTotalCost();
        BigDecimal getTotalDistanceKm();
        BigDecimal getLitresWithDistance();
        long getAnomalyCount();
    }

    interface VehicleTotals extends Totals {
        Long getVehicleId();
        String getPlateNumber();
        String getMake();
        String getModel();
    }
}
