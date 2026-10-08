package com.limoz.fleet.telematics.repository;

import com.limoz.fleet.telematics.domain.VehiclePosition;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface VehiclePositionRepository extends JpaRepository<VehiclePosition, Long> {

    boolean existsByVehicleIdAndRecordedAt(Long vehicleId, Instant recordedAt);

    /** Samples in the half-open window [from, to), oldest first; {@code pageable} caps the row count. */
    @Query("select p from VehiclePosition p where p.vehicleId = :vehicleId and p.recordedAt >= :from and p.recordedAt < :to order by p.recordedAt asc")
    List<VehiclePosition> findInWindow(@Param("vehicleId") Long vehicleId, @Param("from") Instant from, @Param("to") Instant to, Pageable pageable);

    @Query("select count(p) from VehiclePosition p where p.vehicleId = :vehicleId and p.recordedAt >= :from and p.recordedAt < :to")
    long countInWindow(@Param("vehicleId") Long vehicleId, @Param("from") Instant from, @Param("to") Instant to);

    /** Most recent fix of every non-archived vehicle that has at least one position. */
    @Query(value = "select distinct on (p.vehicle_id) p.* from vehicle_positions p "
            + "join vehicles v on v.id = p.vehicle_id where v.archived = false "
            + "order by p.vehicle_id, p.recorded_at desc", nativeQuery = true)
    List<VehiclePosition> findLatestPerVehicle();
}
