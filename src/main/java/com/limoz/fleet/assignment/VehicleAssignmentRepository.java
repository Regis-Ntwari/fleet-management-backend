package com.limoz.fleet.assignment;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface VehicleAssignmentRepository extends JpaRepository<VehicleAssignment, Long>, JpaSpecificationExecutor<VehicleAssignment> {

    @EntityGraph(attributePaths = {"vehicle", "vehicle.category", "driver"})
    Optional<VehicleAssignment> findByVehicleIdAndStatus(Long vehicleId, AssignmentStatus status);

    @EntityGraph(attributePaths = {"vehicle", "vehicle.category", "driver"})
    Optional<VehicleAssignment> findByDriverIdAndStatus(Long driverId, AssignmentStatus status);

    @EntityGraph(attributePaths = {"vehicle", "vehicle.category", "driver"})
    Page<VehicleAssignment> findByVehicleIdOrderByStartAtDesc(Long vehicleId, Pageable pageable);

    @EntityGraph(attributePaths = {"vehicle", "vehicle.category", "driver"})
    Page<VehicleAssignment> findByDriverIdOrderByStartAtDesc(Long driverId, Pageable pageable);

    @EntityGraph(attributePaths = {"vehicle", "vehicle.category", "driver"})
    List<VehicleAssignment> findByStatusOrderByStartAtDesc(AssignmentStatus status);

    @EntityGraph(attributePaths = {"vehicle", "vehicle.category", "driver"})
    Optional<VehicleAssignment> findDetailedById(Long id);

    long countByStatus(AssignmentStatus status);
}
