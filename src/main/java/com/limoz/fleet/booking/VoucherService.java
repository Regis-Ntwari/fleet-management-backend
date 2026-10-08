package com.limoz.fleet.booking;

import com.limoz.fleet.audit.AuditAction;
import com.limoz.fleet.audit.AuditService;
import com.limoz.fleet.booking.dto.ReturnRequest;
import com.limoz.fleet.booking.dto.VoucherFilter;
import com.limoz.fleet.booking.dto.VoucherResponse;
import com.limoz.fleet.booking.dto.VoucherUpdateRequest;
import com.limoz.fleet.common.api.PageResponse;
import com.limoz.fleet.common.exception.BusinessRuleException;
import com.limoz.fleet.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Deployment voucher queries and edits; the dispatch workflow (issue / return) lives in {@link DispatchService}. */
@Service
@RequiredArgsConstructor
@Transactional
public class VoucherService {

    private final DeploymentVoucherRepository voucherRepository;
    private final CommitmentLookupRepository commitmentLookup;
    private final DispatchService dispatchService;
    private final BookingMapper mapper;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public PageResponse<VoucherResponse> search(VoucherFilter filter, Pageable pageable) {
        return PageResponse.from(voucherRepository.findAll(BookingSpecifications.from(filter), pageable).map(mapper::toResponse));
    }

    @Transactional(readOnly = true)
    public VoucherResponse get(Long id) {
        return mapper.toResponse(load(id));
    }

    public VoucherResponse recordReturn(Long id, ReturnRequest request) {
        DeploymentVoucher voucher = load(id);
        return dispatchService.recordReturn(voucher.getSlot().getId(), request);
    }

    /** Edits the commercial fields of a voucher that has not been invoiced or cancelled. */
    public VoucherResponse update(Long id, VoucherUpdateRequest request) {
        DeploymentVoucher voucher = load(id);
        if (voucher.getStatus() == VoucherStatus.INVOICED || voucher.getStatus() == VoucherStatus.CANCELLED) {
            throw new BusinessRuleException("VOUCHER_NOT_EDITABLE", "Voucher " + voucher.getVoucherNumber() + " is " + voucher.getStatus());
        }
        VoucherResponse before = mapper.toResponse(voucher);
        if (request.purchaseOrderId() != null
                && !commitmentLookup.purchaseOrderBelongsTo(request.purchaseOrderId(), voucher.getCustomer().getId())) {
            throw new BusinessRuleException("PURCHASE_ORDER_MISMATCH",
                    "Purchase order " + request.purchaseOrderId() + " does not exist or belongs to another client");
        }
        if (request.purchaseOrderId() != null) {
            voucher.setPurchaseOrderId(request.purchaseOrderId());
        }
        if (request.poAmount() != null) {
            voucher.setPoAmount(request.poAmount());
        }
        if (request.comment() != null) {
            voucher.setComment(request.comment());
        }
        if (request.observation() != null) {
            voucher.setObservation(request.observation());
        }
        if (request.ownerAmount() != null) {
            voucher.setOwnerAmount(request.ownerAmount());
            voucher.setNetAmount(BookingCalculations.netAmount(voucher.getOwnerAmount(), voucher.getFuelAmount()));
        }
        VoucherResponse after = mapper.toResponse(voucherRepository.save(voucher));
        auditService.record(AuditAction.UPDATE, "DeploymentVoucher", id, voucher.getVoucherNumber(), before, after,
                "Voucher " + voucher.getVoucherNumber() + " updated");
        return after;
    }

    private DeploymentVoucher load(Long id) {
        return voucherRepository.findDetailedById(id).orElseThrow(() -> new ResourceNotFoundException("Deployment voucher", id));
    }
}
