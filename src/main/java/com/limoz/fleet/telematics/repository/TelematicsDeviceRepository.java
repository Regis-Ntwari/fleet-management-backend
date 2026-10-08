package com.limoz.fleet.telematics.repository;

import com.limoz.fleet.telematics.domain.FuelSensorStatus;
import com.limoz.fleet.telematics.domain.GpsStatus;
import com.limoz.fleet.telematics.domain.TelematicsDevice;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TelematicsDeviceRepository extends JpaRepository<TelematicsDevice, Long> {

    @EntityGraph(attributePaths = {"vehicle", "vehicle.category"})
    Optional<TelematicsDevice> findDetailedById(Long id);

    @EntityGraph(attributePaths = {"vehicle", "vehicle.category"})
    Optional<TelematicsDevice> findByVehicleId(Long vehicleId);

    boolean existsByVehicleId(Long vehicleId);

    @Query("select count(d) > 0 from TelematicsDevice d where upper(d.providerCode) = upper(:provider) "
            + "and upper(d.externalDeviceId) = upper(:externalId) and (:excludeId is null or d.id <> :excludeId)")
    boolean externalIdExists(@Param("provider") String provider, @Param("externalId") String externalId, @Param("excludeId") Long excludeId);

    @EntityGraph(attributePaths = {"vehicle", "vehicle.category"})
    @Query("select d from TelematicsDevice d where upper(d.externalDeviceId) = upper(:externalId)")
    List<TelematicsDevice> findByExternalDeviceId(@Param("externalId") String externalId);

    /** Devices of non-archived vehicles, optionally narrowed by status; null filters are ignored. */
    @EntityGraph(attributePaths = {"vehicle", "vehicle.category"})
    @Query("select d from TelematicsDevice d where d.vehicle.archived = false "
            + "and (:gpsStatus is null or d.gpsStatus = :gpsStatus) "
            + "and (:fuelStatus is null or d.fuelSensorStatus = :fuelStatus) "
            + "and (:active is null or d.active = :active) order by d.vehicle.plateNumber")
    List<TelematicsDevice> findForActiveVehicles(@Param("gpsStatus") GpsStatus gpsStatus,
                                                 @Param("fuelStatus") FuelSensorStatus fuelStatus,
                                                 @Param("active") Boolean active);

    @EntityGraph(attributePaths = {"vehicle", "vehicle.category"})
    @Query("select d from TelematicsDevice d where d.vehicle.archived = false "
            + "and (d.gpsStatus in :gpsStatuses or d.fuelSensorStatus in :fuelStatuses) order by d.vehicle.plateNumber")
    List<TelematicsDevice> findProblems(@Param("gpsStatuses") Collection<GpsStatus> gpsStatuses,
                                        @Param("fuelStatuses") Collection<FuelSensorStatus> fuelStatuses);

    @EntityGraph(attributePaths = {"vehicle", "vehicle.category"})
    @Query("select d from TelematicsDevice d where d.active = true and d.vehicle.archived = false and d.externalDeviceId is not null")
    List<TelematicsDevice> findActiveWithExternalId();

    @EntityGraph(attributePaths = {"vehicle"})
    @Query("select d from TelematicsDevice d")
    List<TelematicsDevice> findAllWithVehicle();
}
