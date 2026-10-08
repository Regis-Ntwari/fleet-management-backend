package com.limoz.fleet.vehicle.repository;

import com.limoz.fleet.vehicle.domain.VehicleCategory;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VehicleCategoryRepository extends JpaRepository<VehicleCategory, Long> {
    Optional<VehicleCategory> findByCodeIgnoreCase(String code);
    Optional<VehicleCategory> findByNameIgnoreCase(String name);
    List<VehicleCategory> findAllByOrderBySortOrderAscNameAsc();
    boolean existsByCodeIgnoreCase(String code);
    boolean existsByNameIgnoreCase(String name);
}
