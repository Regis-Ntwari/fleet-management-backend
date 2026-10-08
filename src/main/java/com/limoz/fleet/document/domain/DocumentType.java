package com.limoz.fleet.document.domain;

import com.limoz.fleet.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Configurable document/certificate types (insurance, inspection, RURA permit, driving licence...). */
@Entity
@Table(name = "document_types")
@Getter
@Setter
@NoArgsConstructor
public class DocumentType extends BaseEntity {

    @Column(nullable = false, unique = true, length = 40)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "applies_to", nullable = false, length = 10)
    private DocumentAppliesTo appliesTo;

    @Column(name = "required_for_dispatch", nullable = false)
    private boolean requiredForDispatch;

    @Column(name = "warning_days")
    private Integer warningDays;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
}
