package com.limoz.fleet.document;

import com.limoz.fleet.driver.Driver;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "driver_documents")
@Getter
@Setter
@NoArgsConstructor
public class DriverDocument extends AbstractDocument {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "driver_id", nullable = false)
    private Driver driver;

    @Override
    public Long ownerId() {
        return driver.getId();
    }

    @Override
    public String ownerReference() {
        return driver.getFullName();
    }
}
