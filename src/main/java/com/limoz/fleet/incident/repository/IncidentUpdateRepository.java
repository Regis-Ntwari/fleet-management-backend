package com.limoz.fleet.incident.repository;

import com.limoz.fleet.incident.domain.IncidentUpdate;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IncidentUpdateRepository extends JpaRepository<IncidentUpdate, Long> {

    List<IncidentUpdate> findByIncidentIdOrderByCreatedAtAscIdAsc(Long incidentId);
}
