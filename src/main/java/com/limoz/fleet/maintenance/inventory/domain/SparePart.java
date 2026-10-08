package com.limoz.fleet.maintenance.inventory.domain;

import com.limoz.fleet.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/** Catalogue item of the workshop store. {@code currentStock} only changes through stock movements. */
@Entity
@Table(name = "spare_parts")
@Getter
@Setter
@NoArgsConstructor
public class SparePart extends BaseEntity {

    @Column(name = "part_number", nullable = false, length = 40)
    private String partNumber;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 60)
    private String category;

    @Column(nullable = false, length = 20)
    private String unit = "pcs";

    @Column(name = "unit_cost", nullable = false, precision = 14, scale = 2)
    private BigDecimal unitCost = BigDecimal.ZERO;

    @Column(length = 120)
    private String supplier;

    @Column(name = "minimum_stock", nullable = false)
    private int minimumStock;

    @Column(name = "current_stock", nullable = false)
    private int currentStock;

    @Column(length = 80)
    private String location;

    @Column(nullable = false)
    private boolean active = true;

    @Column(length = 255)
    private String notes;

    public StockStatus stockStatus() {
        return StockStatus.of(currentStock, minimumStock);
    }

    public static String normalisePartNumber(String partNumber) {
        return partNumber == null ? null : partNumber.trim().toUpperCase().replaceAll("\\s+", "");
    }
}
