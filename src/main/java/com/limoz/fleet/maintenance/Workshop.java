package com.limoz.fleet.maintenance;

import com.limoz.fleet.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Internal garage or external service vendor that carries out maintenance jobs. */
@Entity
@Table(name = "workshops")
@Getter
@Setter
@NoArgsConstructor
public class Workshop extends BaseEntity {

    @Column(nullable = false, unique = true, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "workshop_type", nullable = false, length = 10)
    private WorkshopType workshopType = WorkshopType.INTERNAL;

    @Column(name = "contact_name", length = 120)
    private String contactName;

    @Column(length = 30)
    private String phone;

    @Column(length = 150)
    private String email;

    @Column(length = 255)
    private String address;

    @Column(nullable = false)
    private boolean active = true;
}
