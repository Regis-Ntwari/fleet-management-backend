package com.limoz.fleet.incident.repository;

import com.limoz.fleet.incident.domain.Incident;
import com.limoz.fleet.incident.domain.IncidentSeverity;
import com.limoz.fleet.incident.domain.IncidentStatus;
import com.limoz.fleet.incident.domain.IncidentType;

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

public interface IncidentRepository extends JpaRepository<Incident, Long>, JpaSpecificationExecutor<Incident> {

    @Override
    @EntityGraph(attributePaths = {"vehicle", "vehicle.category", "driver"})
    Page<Incident> findAll(Specification<Incident> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"vehicle", "vehicle.category", "driver"})
    Optional<Incident> findDetailedById(Long id);

    @EntityGraph(attributePaths = {"vehicle", "vehicle.category", "driver"})
    Page<Incident> findByVehicleIdOrderByOccurredAtDesc(Long vehicleId, Pageable pageable);

    @EntityGraph(attributePaths = {"vehicle", "vehicle.category", "driver"})
    Page<Incident> findByDriverIdOrderByOccurredAtDesc(Long driverId, Pageable pageable);

    @Query("select i.incidentType as key, count(i) as total from Incident i where i.occurredAt >= :from and i.occurredAt < :to group by i.incidentType")
    List<KeyCount<IncidentType>> countByType(@Param("from") Instant from, @Param("to") Instant to);

    @Query("select i.severity as key, count(i) as total from Incident i where i.occurredAt >= :from and i.occurredAt < :to group by i.severity")
    List<KeyCount<IncidentSeverity>> countBySeverity(@Param("from") Instant from, @Param("to") Instant to);

    @Query("select i.status as key, count(i) as total from Incident i where i.occurredAt >= :from and i.occurredAt < :to group by i.status")
    List<KeyCount<IncidentStatus>> countByStatus(@Param("from") Instant from, @Param("to") Instant to);

    @Query("select i.vehicle.id as id, i.vehicle.plateNumber as label, count(i) as total from Incident i "
            + "where i.occurredAt >= :from and i.occurredAt < :to group by i.vehicle.id, i.vehicle.plateNumber order by count(i) desc, i.vehicle.plateNumber asc")
    List<LabelCount> topVehicles(@Param("from") Instant from, @Param("to") Instant to, Pageable pageable);

    @Query("select i.driver.id as id, concat(i.driver.firstName, ' ', i.driver.lastName) as label, count(i) as total from Incident i "
            + "where i.driver is not null and i.occurredAt >= :from and i.occurredAt < :to "
            + "group by i.driver.id, i.driver.firstName, i.driver.lastName order by count(i) desc, i.driver.lastName asc")
    List<LabelCount> topDrivers(@Param("from") Instant from, @Param("to") Instant to, Pageable pageable);

    /** Existence check on the dispatch module's trips table (no Trip entity in this module). */
    @Query(value = "select exists(select 1 from trips t where t.id = :id)", nativeQuery = true)
    boolean tripExists(@Param("id") Long id);

    interface KeyCount<K> {
        K getKey();
        long getTotal();
    }

    interface LabelCount {
        Long getId();
        String getLabel();
        long getTotal();
    }
}
