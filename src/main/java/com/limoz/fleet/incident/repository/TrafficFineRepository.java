package com.limoz.fleet.incident.repository;

import com.limoz.fleet.incident.domain.FineStatus;
import com.limoz.fleet.incident.domain.TrafficFine;

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

public interface TrafficFineRepository extends JpaRepository<TrafficFine, Long>, JpaSpecificationExecutor<TrafficFine> {

    @Override
    @EntityGraph(attributePaths = {"vehicle", "vehicle.category", "driver"})
    Page<TrafficFine> findAll(Specification<TrafficFine> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"vehicle", "vehicle.category", "driver"})
    Optional<TrafficFine> findDetailedById(Long id);

    @EntityGraph(attributePaths = {"vehicle", "vehicle.category", "driver"})
    Page<TrafficFine> findByVehicleIdOrderByIssuedAtDesc(Long vehicleId, Pageable pageable);

    @EntityGraph(attributePaths = {"vehicle", "vehicle.category", "driver"})
    Page<TrafficFine> findByDriverIdOrderByIssuedAtDesc(Long driverId, Pageable pageable);

    @Query("select f.status as status, count(f) as total, coalesce(sum(f.amount), 0) as amount from TrafficFine f "
            + "where f.issuedAt >= :from and f.issuedAt < :to group by f.status")
    List<StatusTotal> totalsByStatus(@Param("from") Instant from, @Param("to") Instant to);

    @Query(value = "select exists(select 1 from trips t where t.id = :id)", nativeQuery = true)
    boolean tripExists(@Param("id") Long id);

    interface StatusTotal {
        FineStatus getStatus();
        long getTotal();
        BigDecimal getAmount();
    }
}
