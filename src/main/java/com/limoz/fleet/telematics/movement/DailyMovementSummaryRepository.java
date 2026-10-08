package com.limoz.fleet.telematics.movement;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface DailyMovementSummaryRepository extends JpaRepository<DailyMovementSummary, Long>, JpaSpecificationExecutor<DailyMovementSummary> {

    Optional<DailyMovementSummary> findByVehicleIdAndSummaryDate(Long vehicleId, LocalDate date);

    @Override
    @EntityGraph(attributePaths = {"vehicle", "vehicle.category"})
    Page<DailyMovementSummary> findAll(Specification<DailyMovementSummary> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"vehicle", "vehicle.category"})
    List<DailyMovementSummary> findByVehicleIdAndSummaryDateBetweenOrderBySummaryDateAsc(Long vehicleId, LocalDate from, LocalDate to);

    @EntityGraph(attributePaths = {"vehicle", "vehicle.category"})
    @Query("select s from DailyMovementSummary s where s.summaryDate = :date and s.vehicle.archived = false order by s.vehicle.plateNumber")
    List<DailyMovementSummary> findByDate(@Param("date") LocalDate date);

    /** Vehicles that moved on at least one day of the inclusive range. */
    @Query("select distinct s.vehicle.id from DailyMovementSummary s where s.summaryDate between :from and :to and s.moved = true")
    Set<Long> vehicleIdsMovedBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
