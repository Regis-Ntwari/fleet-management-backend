package com.limoz.fleet.booking;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long>, JpaSpecificationExecutor<Booking> {

    @EntityGraph(attributePaths = {"customer", "lines", "lines.category"})
    Optional<Booking> findDetailedById(Long id);

    @Override
    @EntityGraph(attributePaths = {"customer"})
    Page<Booking> findAll(Specification<Booking> spec, Pageable pageable);

    @Query("select b.status as status, count(b) as total from Booking b group by b.status")
    List<StatusCount> countByStatus();

    interface StatusCount {
        BookingStatus getStatus();
        long getTotal();
    }
}
