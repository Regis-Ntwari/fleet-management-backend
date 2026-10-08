package com.limoz.fleet.finance.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Keeps {@code maintenance_records.amount_paid / payment_status} in sync with the OUT payments linked to a
 * job. The maintenance aggregate is owned by the workshop module, so rather than importing its entity
 * (which would couple the finance module to that module's lifecycle rules and risk stale-entity version
 * clashes) the two ledger columns are recomputed with a single set-based update from the payments table.
 */
@Component
@RequiredArgsConstructor
public class MaintenanceLedgerUpdater {

    private static final String RECOMPUTE =
            "update maintenance_records m set amount_paid = s.total, "
            + "payment_status = case when m.total_cost > 0 and s.total >= m.total_cost then 'PAID' when s.total > 0 then 'PARTIAL' else 'UNPAID' end, "
            + "updated_at = now() "
            + "from (select coalesce(sum(p.amount), 0) as total from payments p "
            + "      where p.maintenance_record_id = :id and p.direction = 'OUT' and p.reversed = false) s "
            + "where m.id = :id";

    private final JdbcClient jdbcClient;

    /** Must be called after the payment rows have been flushed, since it reads the payments table directly. */
    public void recomputeAmountPaid(Long maintenanceRecordId) {
        jdbcClient.sql(RECOMPUTE).param("id", maintenanceRecordId).update();
    }
}
