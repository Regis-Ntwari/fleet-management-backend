package com.limoz.fleet.trip.repository;

import com.limoz.fleet.trip.domain.Trip;
import com.limoz.fleet.trip.domain.TripStatus;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TripRepository extends JpaRepository<Trip, Long>, JpaSpecificationExecutor<Trip> {

    @EntityGraph(attributePaths = {"vehicle", "vehicle.category", "driver", "customer", "booking"})
    Optional<Trip> findDetailedById(Long id);

    @Override
    @EntityGraph(attributePaths = {"vehicle", "vehicle.category", "driver", "customer", "booking"})
    Page<Trip> findAll(Specification<Trip> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"vehicle", "vehicle.category", "driver", "customer", "booking"})
    List<Trip> findByStatusOrderByStartedAtAsc(TripStatus status);

    /** Active trips of the vehicle whose scheduled window overlaps [from, to) - open-ended windows end at their start. */
    @EntityGraph(attributePaths = {"vehicle", "vehicle.category", "driver", "customer", "booking"})
    @Query("""
            select t from Trip t
            where t.vehicle.id = :vehicleId and t.status in :statuses
              and t.scheduledStartAt < :to and coalesce(t.scheduledEndAt, t.scheduledStartAt) >= :from
              and (:excludeId is null or t.id <> :excludeId)
            order by t.scheduledStartAt asc""")
    List<Trip> findOverlappingForVehicle(@Param("vehicleId") Long vehicleId, @Param("from") Instant from, @Param("to") Instant to,
                                         @Param("statuses") List<TripStatus> statuses, @Param("excludeId") Long excludeId);

    @EntityGraph(attributePaths = {"vehicle", "vehicle.category", "driver", "customer", "booking"})
    @Query("""
            select t from Trip t
            where t.driver.id = :driverId and t.status in :statuses
              and t.scheduledStartAt < :to and coalesce(t.scheduledEndAt, t.scheduledStartAt) >= :from
              and (:excludeId is null or t.id <> :excludeId)
            order by t.scheduledStartAt asc""")
    List<Trip> findOverlappingForDriver(@Param("driverId") Long driverId, @Param("from") Instant from, @Param("to") Instant to,
                                        @Param("statuses") List<TripStatus> statuses, @Param("excludeId") Long excludeId);

    /** All active trips whose scheduled window overlaps [from, to) (availability calendar, board). */
    @EntityGraph(attributePaths = {"vehicle", "vehicle.category", "driver", "customer", "booking"})
    @Query("""
            select t from Trip t
            where t.status in :statuses
              and t.scheduledStartAt < :to and coalesce(t.scheduledEndAt, t.scheduledStartAt) >= :from
            order by t.scheduledStartAt asc""")
    List<Trip> findOverlapping(@Param("from") Instant from, @Param("to") Instant to, @Param("statuses") List<TripStatus> statuses);
}
