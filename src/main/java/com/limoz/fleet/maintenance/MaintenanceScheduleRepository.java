package com.limoz.fleet.maintenance;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface MaintenanceScheduleRepository extends JpaRepository<MaintenanceSchedule, Long>, JpaSpecificationExecutor<MaintenanceSchedule> {

    @EntityGraph(attributePaths = {"vehicle", "vehicle.category", "serviceType"})
    Optional<MaintenanceSchedule> findDetailedById(Long id);

    @Override
    @EntityGraph(attributePaths = {"vehicle", "vehicle.category", "serviceType"})
    Page<MaintenanceSchedule> findAll(Specification<MaintenanceSchedule> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"vehicle", "vehicle.category", "serviceType"})
    Optional<MaintenanceSchedule> findByVehicleIdAndServiceTypeId(Long vehicleId, Long serviceTypeId);

    @EntityGraph(attributePaths = {"vehicle", "vehicle.category", "serviceType"})
    List<MaintenanceSchedule> findByVehicleIdOrderByNextServiceDateAscNextServiceOdometerAsc(Long vehicleId);

    @EntityGraph(attributePaths = {"vehicle", "vehicle.category", "serviceType"})
    List<MaintenanceSchedule> findByActiveTrueAndVehicleArchivedFalse();

    @EntityGraph(attributePaths = {"vehicle", "serviceType"})
    List<MaintenanceSchedule> findByVehicleIdAndActiveTrue(Long vehicleId);

    long countByServiceTypeId(Long serviceTypeId);
}
