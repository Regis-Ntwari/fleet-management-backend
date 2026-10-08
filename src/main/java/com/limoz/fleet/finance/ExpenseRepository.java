package com.limoz.fleet.finance;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ExpenseRepository extends JpaRepository<Expense, Long>, JpaSpecificationExecutor<Expense> {

    @Override
    @EntityGraph(attributePaths = {"category", "vehicle", "vehicle.category", "driver"})
    Page<Expense> findAll(Specification<Expense> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"category", "vehicle", "vehicle.category", "driver"})
    Optional<Expense> findDetailedById(Long id);

    long countByCategoryId(Long categoryId);

    @Query(value = "select exists(select 1 from trips t where t.id = :id)", nativeQuery = true)
    boolean tripExists(@Param("id") Long id);

    @Query(value = "select exists(select 1 from bookings b where b.id = :id)", nativeQuery = true)
    boolean bookingExists(@Param("id") Long id);
}
