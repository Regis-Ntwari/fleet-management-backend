package com.limoz.fleet.maintenance.repository;

import com.limoz.fleet.maintenance.domain.Workshop;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface WorkshopRepository extends JpaRepository<Workshop, Long> {

    List<Workshop> findAllByOrderByNameAsc();

    @Query("select count(w) > 0 from Workshop w where lower(w.name) = lower(:name) and (:excludeId is null or w.id <> :excludeId)")
    boolean nameExists(@Param("name") String name, @Param("excludeId") Long excludeId);
}
