package com.limoz.fleet.booking;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface DeploymentVoucherRepository extends JpaRepository<DeploymentVoucher, Long>, JpaSpecificationExecutor<DeploymentVoucher> {

    @EntityGraph(attributePaths = {"slot", "booking", "customer", "vehicle", "vehicle.category", "driver"})
    Optional<DeploymentVoucher> findDetailedById(Long id);

    @EntityGraph(attributePaths = {"slot", "booking", "customer", "vehicle", "vehicle.category", "driver"})
    Optional<DeploymentVoucher> findBySlotId(Long slotId);

    @EntityGraph(attributePaths = {"slot", "booking", "customer", "vehicle", "vehicle.category", "driver"})
    List<DeploymentVoucher> findByBookingIdOrderByVoucherDateAscIdAsc(Long bookingId);

    @Override
    @EntityGraph(attributePaths = {"slot", "booking", "customer", "vehicle", "vehicle.category", "driver"})
    Page<DeploymentVoucher> findAll(Specification<DeploymentVoucher> spec, Pageable pageable);
}
