package com.limoz.fleet.customer;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CommitmentRepository extends JpaRepository<Commitment, Long>, JpaSpecificationExecutor<Commitment> {

    @Override
    @EntityGraph(attributePaths = "customer")
    Page<Commitment> findAll(Specification<Commitment> spec, Pageable pageable);

    @EntityGraph(attributePaths = "customer")
    Optional<Commitment> findDetailedById(Long id);

    @EntityGraph(attributePaths = "customer")
    List<Commitment> findByCustomerIdOrderByPeriodEndDesc(Long customerId);

    @EntityGraph(attributePaths = "customer")
    List<Commitment> findByCustomerIdAndStatusInOrderByReferenceAsc(Long customerId, Collection<CommitmentStatus> statuses);

    @EntityGraph(attributePaths = "customer")
    List<Commitment> findByStatusInOrderByReferenceAsc(Collection<CommitmentStatus> statuses);

    @EntityGraph(attributePaths = "customer")
    List<Commitment> findByStatusIn(Collection<CommitmentStatus> statuses);

    long countByCustomerIdAndStatusNot(Long customerId, CommitmentStatus status);

    /**
     * Value drawn down by non-cancelled bookings. The Booking aggregate belongs to the dispatch module,
     * so this is a read-only aggregate over the table rather than an entity association.
     */
    @Query(value = "select coalesce(sum(b.total_amount), 0) from bookings b where b.commitment_id = :id and b.status <> 'CANCELLED'",
            nativeQuery = true)
    BigDecimal consumedValue(@Param("id") Long id);

    @Query(value = "select b.commitment_id as commitmentId, coalesce(sum(b.total_amount), 0) as consumed from bookings b "
            + "where b.commitment_id in (:ids) and b.status <> 'CANCELLED' group by b.commitment_id", nativeQuery = true)
    List<ConsumedValue> consumedValues(@Param("ids") Collection<Long> ids);

    interface ConsumedValue {
        Long getCommitmentId();
        BigDecimal getConsumed();
    }
}
