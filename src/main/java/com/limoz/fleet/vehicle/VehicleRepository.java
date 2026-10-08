package com.limoz.fleet.vehicle;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface VehicleRepository extends JpaRepository<Vehicle, Long>, JpaSpecificationExecutor<Vehicle> {

    @Query("select v from Vehicle v where upper(replace(v.plateNumber, ' ', '')) = upper(replace(:plate, ' ', ''))")
    Optional<Vehicle> findByPlate(@Param("plate") String plate);

    @Query("select count(v) > 0 from Vehicle v where upper(replace(v.plateNumber, ' ', '')) = upper(replace(:plate, ' ', '')) and (:excludeId is null or v.id <> :excludeId)")
    boolean plateExists(@Param("plate") String plate, @Param("excludeId") Long excludeId);

    @EntityGraph(attributePaths = {"category", "currentDriver"})
    Optional<Vehicle> findDetailedById(Long id);

    @EntityGraph(attributePaths = {"category", "currentDriver"})
    List<Vehicle> findByArchivedFalseAndOperationalStatusInOrderByPlateNumberAsc(List<VehicleStatus> statuses);

    @EntityGraph(attributePaths = {"category", "currentDriver"})
    List<Vehicle> findByArchivedFalseOrderByPlateNumberAsc();

    long countByArchivedFalse();

    long countByArchivedFalseAndOperationalStatus(VehicleStatus status);

    @Query("select v.operationalStatus as status, count(v) as total from Vehicle v where v.archived = false group by v.operationalStatus")
    List<StatusCount> countByStatus();

    @Query("select v.category.name as name, count(v) as total from Vehicle v where v.archived = false group by v.category.name order by v.category.name")
    List<CategoryCount> countByCategory();

    interface StatusCount {
        VehicleStatus getStatus();
        long getTotal();
    }

    interface CategoryCount {
        String getName();
        long getTotal();
    }
}
