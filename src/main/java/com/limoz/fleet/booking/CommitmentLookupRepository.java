package com.limoz.fleet.booking;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Read-only access to the commitments and purchase orders tables, which are owned by the contracts /
 * finance modules. Bookings only need to validate references and show the commitment reference.
 */
@Repository
@Transactional(readOnly = true)
public class CommitmentLookupRepository {

    @PersistenceContext
    private EntityManager entityManager;

    /** Minimal projection of a commitment row. */
    public record CommitmentRef(Long id, Long customerId, String reference, String title, String status) {

        /** Bookings may only draw down against live contracts. */
        public boolean isDrawable() {
            return "ACTIVE".equals(status) || "EXPIRING_SOON".equals(status);
        }
    }

    public Optional<CommitmentRef> findCommitment(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery(
                        "select id, customer_id, reference, title, status from commitments where id = :id")
                .setParameter("id", id)
                .getResultList();
        return rows.stream().findFirst().map(r -> new CommitmentRef(
                ((Number) r[0]).longValue(), ((Number) r[1]).longValue(), (String) r[2], (String) r[3], (String) r[4]));
    }

    /** True when the purchase order exists and was raised by the given customer. */
    public boolean purchaseOrderBelongsTo(Long purchaseOrderId, Long customerId) {
        Number count = (Number) entityManager.createNativeQuery(
                        "select count(*) from purchase_orders where id = :id and customer_id = :customerId")
                .setParameter("id", purchaseOrderId)
                .setParameter("customerId", customerId)
                .getSingleResult();
        return count.longValue() > 0;
    }
}
