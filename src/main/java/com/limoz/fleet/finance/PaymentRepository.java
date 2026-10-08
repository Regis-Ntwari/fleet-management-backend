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
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long>, JpaSpecificationExecutor<Payment> {

    @Override
    @EntityGraph(attributePaths = {"customer", "invoice", "expense"})
    Page<Payment> findAll(Specification<Payment> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"customer", "invoice", "expense"})
    Optional<Payment> findDetailedById(Long id);

    @EntityGraph(attributePaths = {"customer", "invoice", "expense"})
    List<Payment> findByInvoiceIdOrderByPaidAtDesc(Long invoiceId);

    boolean existsByInvoiceIdAndReversedFalse(Long invoiceId);

    boolean existsByExpenseIdAndReversedFalse(Long expenseId);

    @Query("select coalesce(sum(p.amount), 0) from Payment p where p.invoice.id = :invoiceId and p.direction = :direction and p.reversed = false")
    BigDecimal sumByInvoice(@Param("invoiceId") Long invoiceId, @Param("direction") PaymentDirection direction);

    @Query(value = "select exists(select 1 from maintenance_records m where m.id = :id)", nativeQuery = true)
    boolean maintenanceRecordExists(@Param("id") Long id);
}
