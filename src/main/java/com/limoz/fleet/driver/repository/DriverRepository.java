package com.limoz.fleet.driver.repository;

import com.limoz.fleet.driver.domain.Driver;
import com.limoz.fleet.driver.domain.DriverStatus;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DriverRepository extends JpaRepository<Driver, Long>, JpaSpecificationExecutor<Driver> {

    @EntityGraph(attributePaths = {"currentVehicle", "currentVehicle.category"})
    Optional<Driver> findDetailedById(Long id);

    @Query("select count(d) > 0 from Driver d where upper(d.licenseNumber) = upper(:license) and (:excludeId is null or d.id <> :excludeId)")
    boolean licenseExists(@Param("license") String license, @Param("excludeId") Long excludeId);

    @Query("select count(d) > 0 from Driver d where d.nationalId = :nationalId and (:excludeId is null or d.id <> :excludeId)")
    boolean nationalIdExists(@Param("nationalId") String nationalId, @Param("excludeId") Long excludeId);

    Optional<Driver> findByLicenseNumberIgnoreCase(String licenseNumber);

    @EntityGraph(attributePaths = {"currentVehicle", "currentVehicle.category"})
    List<Driver> findByArchivedFalseAndStatusInOrderByLastNameAscFirstNameAsc(List<DriverStatus> statuses);

    @EntityGraph(attributePaths = {"currentVehicle", "currentVehicle.category"})
    List<Driver> findByArchivedFalseOrderByLastNameAscFirstNameAsc();

    long countByArchivedFalse();

    long countByArchivedFalseAndStatus(DriverStatus status);

    long countByArchivedFalseAndStatusIn(List<DriverStatus> statuses);

    List<Driver> findByArchivedFalseAndLicenseExpiryDateBetween(LocalDate from, LocalDate to);

    List<Driver> findByArchivedFalseAndLicenseExpiryDateBefore(LocalDate date);

    @Query("select d.status as status, count(d) as total from Driver d where d.archived = false group by d.status")
    List<StatusCount> countByStatus();

    interface StatusCount {
        DriverStatus getStatus();
        long getTotal();
    }
}
