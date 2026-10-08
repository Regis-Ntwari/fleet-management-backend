package com.limoz.fleet.maintenance.inventory;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface SparePartRepository extends JpaRepository<SparePart, Long>, JpaSpecificationExecutor<SparePart> {

    /** Row-locked load used while a stock movement is applied, so concurrent issues cannot overdraw the stock. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from SparePart p where p.id = :id")
    Optional<SparePart> lockById(@Param("id") Long id);

    @Query("select count(p) > 0 from SparePart p where upper(p.partNumber) = upper(:partNumber) and (:excludeId is null or p.id <> :excludeId)")
    boolean partNumberExists(@Param("partNumber") String partNumber, @Param("excludeId") Long excludeId);

    @Query("select p from SparePart p where p.active = true and p.currentStock <= p.minimumStock order by (p.currentStock - p.minimumStock) asc, p.name asc")
    List<SparePart> findLowStock();

    @Query("""
            select count(p) as partCount,
                   coalesce(sum(case when p.currentStock <= 0 then 1 else 0 end), 0) as outOfStockCount,
                   coalesce(sum(case when p.currentStock > 0 and p.currentStock <= p.minimumStock then 1 else 0 end), 0) as lowStockCount,
                   coalesce(sum(p.currentStock * p.unitCost), 0) as stockValue
            from SparePart p where p.active = true
            """)
    InventoryTotals totals();

    interface InventoryTotals {
        long getPartCount();
        long getOutOfStockCount();
        long getLowStockCount();
        BigDecimal getStockValue();
    }
}
