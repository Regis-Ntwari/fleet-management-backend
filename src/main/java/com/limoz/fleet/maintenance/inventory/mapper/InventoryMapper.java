package com.limoz.fleet.maintenance.inventory.mapper;

import com.limoz.fleet.maintenance.inventory.domain.SparePart;
import com.limoz.fleet.maintenance.inventory.domain.StockMovement;

import com.limoz.fleet.maintenance.inventory.dto.SparePartResponse;
import com.limoz.fleet.maintenance.inventory.dto.StockMovementResponse;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class InventoryMapper {

    public SparePartResponse toResponse(SparePart p) {
        BigDecimal value = p.getUnitCost().multiply(BigDecimal.valueOf(p.getCurrentStock())).setScale(2, RoundingMode.HALF_UP);
        return new SparePartResponse(p.getId(), p.getPartNumber(), p.getName(), p.getCategory(), p.getUnit(), p.getUnitCost(),
                p.getSupplier(), p.getMinimumStock(), p.getCurrentStock(), p.stockStatus(), value, p.getLocation(), p.isActive(),
                p.getNotes(), p.getCreatedAt(), p.getUpdatedAt());
    }

    public StockMovementResponse toResponse(StockMovement m) {
        SparePart part = m.getSparePart();
        return new StockMovementResponse(m.getId(), part.getId(), part.getPartNumber(), part.getName(), m.getMovementType(),
                m.getQuantity(), m.getUnitCost(), m.getBalanceAfter(), m.getReferenceType(), m.getReferenceId(), m.getReferenceNumber(),
                m.getMaintenanceRecord() == null ? null : m.getMaintenanceRecord().getId(), m.getPerformedByUserId(),
                m.getPerformedByName(), m.getMovedAt(), m.getNotes(), m.getCreatedAt());
    }
}
