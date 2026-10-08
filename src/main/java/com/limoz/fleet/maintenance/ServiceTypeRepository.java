package com.limoz.fleet.maintenance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ServiceTypeRepository extends JpaRepository<ServiceType, Long> {

    List<ServiceType> findAllByOrderBySortOrderAscNameAsc();

    Optional<ServiceType> findByCodeIgnoreCase(String code);

    @Query("select count(s) > 0 from ServiceType s where upper(s.code) = upper(:code) and (:excludeId is null or s.id <> :excludeId)")
    boolean codeExists(@Param("code") String code, @Param("excludeId") Long excludeId);
}
