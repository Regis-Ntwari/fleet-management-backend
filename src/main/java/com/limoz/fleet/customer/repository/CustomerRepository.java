package com.limoz.fleet.customer.repository;

import com.limoz.fleet.customer.domain.Customer;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long>, JpaSpecificationExecutor<Customer> {

    Optional<Customer> findByNameIgnoreCase(String name);

    @Query("select count(c) > 0 from Customer c where lower(c.name) = lower(:name) and (:excludeId is null or c.id <> :excludeId)")
    boolean nameExists(@Param("name") String name, @Param("excludeId") Long excludeId);

    @Query("select count(c) > 0 from Customer c where replace(c.tin, ' ', '') = replace(:tin, ' ', '') and (:excludeId is null or c.id <> :excludeId)")
    boolean tinExists(@Param("tin") String tin, @Param("excludeId") Long excludeId);

    List<Customer> findByActiveTrueOrderByNameAsc();

    long countByActiveTrue();
}
