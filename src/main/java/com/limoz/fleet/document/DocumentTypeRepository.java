package com.limoz.fleet.document;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DocumentTypeRepository extends JpaRepository<DocumentType, Long> {
    List<DocumentType> findAllByOrderBySortOrderAscNameAsc();
    Optional<DocumentType> findByCodeIgnoreCase(String code);
    boolean existsByCodeIgnoreCase(String code);
    List<DocumentType> findByRequiredForDispatchTrueAndActiveTrueAndAppliesToIn(List<DocumentAppliesTo> appliesTo);
}
