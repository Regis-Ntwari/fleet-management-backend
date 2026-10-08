package com.limoz.fleet.customer;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long>, JpaSpecificationExecutor<PurchaseOrder> {

    @Override
    @EntityGraph(attributePaths = {"customer", "commitment"})
    Page<PurchaseOrder> findAll(Specification<PurchaseOrder> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"customer", "commitment"})
    Optional<PurchaseOrder> findDetailedById(Long id);

    @EntityGraph(attributePaths = {"customer", "commitment"})
    List<PurchaseOrder> findByCustomerIdOrderByIssuedDateDesc(Long customerId);

    @EntityGraph(attributePaths = {"customer", "commitment"})
    List<PurchaseOrder> findByBookingIdAndStatusIn(Long bookingId, Collection<PurchaseOrderStatus> statuses);

    @EntityGraph(attributePaths = {"customer", "commitment"})
    List<PurchaseOrder> findByStatusIn(Collection<PurchaseOrderStatus> statuses);

    long countByCustomerIdAndStatusIn(Long customerId, Collection<PurchaseOrderStatus> statuses);

    @Query("select p.commitment.id as commitmentId, count(p) as total from PurchaseOrder p "
            + "where p.commitment.id in :ids and p.status <> :excluded group by p.commitment.id")
    List<CommitmentCount> countByCommitmentIds(@Param("ids") Collection<Long> ids, @Param("excluded") PurchaseOrderStatus excluded);

    @Query("select count(p) from PurchaseOrder p where p.commitment.id = :id and p.status <> :excluded")
    long countByCommitment(@Param("id") Long commitmentId, @Param("excluded") PurchaseOrderStatus excluded);

    /** Existence/ownership check on the dispatch module's bookings table (no Booking entity in this module). */
    @Query(value = "select exists(select 1 from bookings b where b.id = :bookingId and b.customer_id = :customerId)", nativeQuery = true)
    boolean bookingBelongsToCustomer(@Param("bookingId") Long bookingId, @Param("customerId") Long customerId);

    @Query(value = "select b.id as bookingId, b.booking_number as bookingNumber from bookings b where b.id in (:ids)", nativeQuery = true)
    List<BookingRef> bookingReferences(@Param("ids") Collection<Long> ids);

    interface CommitmentCount {
        Long getCommitmentId();
        long getTotal();
    }

    interface BookingRef {
        Long getBookingId();
        String getBookingNumber();
    }
}
