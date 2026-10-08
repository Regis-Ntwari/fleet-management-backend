package com.limoz.fleet.maintenance.inventory;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long>, JpaSpecificationExecutor<StockMovement> {

    @EntityGraph(attributePaths = {"sparePart"})
    Optional<StockMovement> findDetailedById(Long id);

    @Override
    @EntityGraph(attributePaths = {"sparePart"})
    Page<StockMovement> findAll(Specification<StockMovement> spec, Pageable pageable);

    long countBySparePartId(Long sparePartId);
}
