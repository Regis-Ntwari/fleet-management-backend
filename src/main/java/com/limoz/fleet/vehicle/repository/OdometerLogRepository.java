package com.limoz.fleet.vehicle.repository;

import com.limoz.fleet.vehicle.domain.OdometerLog;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface OdometerLogRepository extends JpaRepository<OdometerLog, Long> {
    Page<OdometerLog> findByVehicleIdOrderByRecordedAtDesc(Long vehicleId, Pageable pageable);
    List<OdometerLog> findByVehicleIdAndRecordedAtBetweenOrderByRecordedAtAsc(Long vehicleId, Instant from, Instant to);
}
