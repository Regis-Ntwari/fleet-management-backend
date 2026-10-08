package com.limoz.fleet.document.repository;

import com.limoz.fleet.document.domain.DocumentStatus;
import com.limoz.fleet.document.domain.DriverDocument;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DriverDocumentRepository extends JpaRepository<DriverDocument, Long> {

    @EntityGraph(attributePaths = {"documentType", "driver"})
    List<DriverDocument> findByDriverIdOrderBySupersededAscExpiryDateDesc(Long driverId);

    @EntityGraph(attributePaths = {"documentType", "driver"})
    Optional<DriverDocument> findDetailedById(Long id);

    @EntityGraph(attributePaths = {"documentType", "driver"})
    List<DriverDocument> findBySupersededFalse();

    @EntityGraph(attributePaths = {"documentType", "driver"})
    @Query("select d from DriverDocument d where d.superseded = false and d.driver.archived = false and d.status in :statuses order by d.expiryDate asc")
    List<DriverDocument> findActiveByStatusIn(@Param("statuses") List<DocumentStatus> statuses);

    @Query("select count(d) from DriverDocument d where d.superseded = false and d.driver.archived = false and d.status = :status")
    long countActiveByStatus(@Param("status") DocumentStatus status);
}
