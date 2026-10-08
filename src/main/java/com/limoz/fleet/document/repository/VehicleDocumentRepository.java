package com.limoz.fleet.document.repository;

import com.limoz.fleet.document.domain.DocumentStatus;
import com.limoz.fleet.document.domain.VehicleDocument;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface VehicleDocumentRepository extends JpaRepository<VehicleDocument, Long>, JpaSpecificationExecutor<VehicleDocument> {

    @EntityGraph(attributePaths = {"documentType", "vehicle"})
    List<VehicleDocument> findByVehicleIdOrderBySupersededAscExpiryDateDesc(Long vehicleId);

    @EntityGraph(attributePaths = {"documentType", "vehicle"})
    Optional<VehicleDocument> findDetailedById(Long id);

    @EntityGraph(attributePaths = {"documentType", "vehicle"})
    List<VehicleDocument> findByVehicleIdAndSupersededFalse(Long vehicleId);

    @EntityGraph(attributePaths = {"documentType", "vehicle"})
    List<VehicleDocument> findBySupersededFalse();

    @EntityGraph(attributePaths = {"documentType", "vehicle", "vehicle.category"})
    @Query("select d from VehicleDocument d where d.superseded = false and d.vehicle.archived = false and d.status in :statuses order by d.expiryDate asc")
    List<VehicleDocument> findActiveByStatusIn(@Param("statuses") List<DocumentStatus> statuses);

    @EntityGraph(attributePaths = {"documentType", "vehicle", "vehicle.category"})
    @Query("select d from VehicleDocument d where d.superseded = false and d.vehicle.archived = false and d.status in :statuses")
    Page<VehicleDocument> pageActiveByStatusIn(@Param("statuses") List<DocumentStatus> statuses, Pageable pageable);

    @Query("select count(d) from VehicleDocument d where d.superseded = false and d.vehicle.archived = false and d.status = :status")
    long countActiveByStatus(@Param("status") DocumentStatus status);

    @Query("select count(distinct d.vehicle.id) from VehicleDocument d where d.superseded = false and d.vehicle.archived = false and d.status = :status")
    long countVehiclesWithStatus(@Param("status") DocumentStatus status);

    List<VehicleDocument> findBySupersededFalseAndExpiryDateBetween(LocalDate from, LocalDate to);
}
