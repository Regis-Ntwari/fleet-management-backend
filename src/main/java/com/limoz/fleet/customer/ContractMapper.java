package com.limoz.fleet.customer;

import com.limoz.fleet.customer.dto.CommitmentResponse;
import com.limoz.fleet.customer.dto.CommitmentSummary;
import com.limoz.fleet.customer.dto.PurchaseOrderResponse;
import com.limoz.fleet.customer.dto.PurchaseOrderSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/** Hand-written mapping for commitments and purchase orders (the client's contractual documents). */
@Component
@RequiredArgsConstructor
public class ContractMapper {

    private final CustomerMapper customerMapper;

    public CommitmentResponse toResponse(Commitment c, BigDecimal consumed, long lpoCount) {
        BigDecimal used = consumed == null ? BigDecimal.ZERO : consumed;
        BigDecimal remaining = c.getContractedValue().subtract(used);
        return new CommitmentResponse(c.getId(), c.getReference(), customerMapper.toSummary(c.getCustomer()), c.getTitle(),
                c.getPeriodStart(), c.getPeriodEnd(), c.getContractedValue(), c.getCurrency(), used, remaining,
                utilisation(c.getContractedValue(), used), lpoCount, c.getStatus(), c.getAttachmentId(), c.getNotes(),
                c.getCreatedAt(), c.getUpdatedAt(), c.getCreatedBy());
    }

    public CommitmentSummary toSummary(Commitment c) {
        return c == null ? null : new CommitmentSummary(c.getId(), c.getReference(), c.getTitle(), c.getCustomer().getId(),
                c.getPeriodStart(), c.getPeriodEnd(), c.getContractedValue(), c.getCurrency(), c.getStatus());
    }

    public PurchaseOrderResponse toResponse(PurchaseOrder p, String bookingNumber, LocalDate today) {
        boolean expired = p.getStatus() == PurchaseOrderStatus.EXPIRED
                || (p.getExpiryDate() != null && p.getExpiryDate().isBefore(today) && !p.getStatus().isFinal());
        return new PurchaseOrderResponse(p.getId(), p.getLpoNumber(), customerMapper.toSummary(p.getCustomer()), toSummary(p.getCommitment()),
                p.getBookingId(), bookingNumber, p.getIssuedDate(), p.getExpiryDate(), p.getReceivedDate(), p.getValue(), p.getCurrency(),
                p.getStatus(), expired, p.getAttachmentId(), p.getNotes(), p.getCreatedAt(), p.getUpdatedAt(), p.getCreatedBy());
    }

    public PurchaseOrderSummary toSummary(PurchaseOrder p) {
        return p == null ? null : new PurchaseOrderSummary(p.getId(), p.getLpoNumber(), p.getCustomer().getId(), p.getValue(),
                p.getCurrency(), p.getStatus());
    }

    /** Percentage of the contracted value already drawn down (one decimal, 0 when nothing is contracted). */
    public static BigDecimal utilisation(BigDecimal contracted, BigDecimal consumed) {
        if (contracted == null || contracted.signum() <= 0) {
            return BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP);
        }
        return consumed.multiply(BigDecimal.valueOf(100)).divide(contracted, 1, RoundingMode.HALF_UP);
    }
}
