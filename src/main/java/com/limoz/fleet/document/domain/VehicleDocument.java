package com.limoz.fleet.document.domain;

import com.limoz.fleet.vehicle.domain.Vehicle;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "vehicle_documents")
@Getter
@Setter
@NoArgsConstructor
public class VehicleDocument extends AbstractDocument {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    @Column(precision = 14, scale = 2)
    private BigDecimal cost;

    @Override
    public Long ownerId() {
        return vehicle.getId();
    }

    @Override
    public String ownerReference() {
        return vehicle.getPlateNumber();
    }
}
