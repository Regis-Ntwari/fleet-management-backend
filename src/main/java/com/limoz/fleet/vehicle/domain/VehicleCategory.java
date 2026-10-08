package com.limoz.fleet.vehicle.domain;

import com.limoz.fleet.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "vehicle_categories")
@Getter
@Setter
@NoArgsConstructor
public class VehicleCategory extends BaseEntity {

    @Column(nullable = false, unique = true, length = 30)
    private String code;

    @Column(nullable = false, unique = true, length = 80)
    private String name;

    @Column(length = 255)
    private String description;

    @Column(name = "min_seats")
    private Integer minSeats;

    @Column(name = "max_seats")
    private Integer maxSeats;

    @Column(name = "default_day_rate", precision = 14, scale = 2)
    private BigDecimal defaultDayRate;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
}
