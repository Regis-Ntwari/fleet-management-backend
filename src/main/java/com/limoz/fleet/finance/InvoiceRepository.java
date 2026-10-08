package com.limoz.fleet.finance;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface InvoiceRepository extends JpaRepository<Invoice, Long>, JpaSpecificationExecutor<Invoice> {

    @Override
    @EntityGraph(attributePaths = {"customer", "purchaseOrder", "purchaseOrder.customer"})
    Page<Invoice> findAll(Specification<Invoice> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"customer", "purchaseOrder", "purchaseOrder.customer", "lines"})
    Optional<Invoice> findDetailedById(Long id);

    @EntityGraph(attributePaths = {"customer", "purchaseOrder", "purchaseOrder.customer"})
    List<Invoice> findByStatusInAndDueDateBefore(Collection<InvoiceStatus> statuses, LocalDate date);

    boolean existsByBookingIdAndStatusNot(Long bookingId, InvoiceStatus status);

    @Query("select coalesce(sum(i.totalAmount), 0) from Invoice i where i.purchaseOrder.id = :purchaseOrderId and i.status in :statuses")
    BigDecimal sumTotalByPurchaseOrder(@Param("purchaseOrderId") Long purchaseOrderId, @Param("statuses") Collection<InvoiceStatus> statuses);

    @Query(value = "select exists(select 1 from bookings b where b.id = :bookingId and b.customer_id = :customerId)", nativeQuery = true)
    boolean bookingBelongsToCustomer(@Param("bookingId") Long bookingId, @Param("customerId") Long customerId);
}
