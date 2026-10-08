package com.limoz.fleet.finance;

import com.limoz.fleet.customer.ContractMapper;
import com.limoz.fleet.customer.CustomerMapper;
import com.limoz.fleet.driver.DriverMapper;
import com.limoz.fleet.finance.dto.ExpenseCategoryResponse;
import com.limoz.fleet.finance.dto.ExpenseResponse;
import com.limoz.fleet.finance.dto.InvoiceLineResponse;
import com.limoz.fleet.finance.dto.InvoiceResponse;
import com.limoz.fleet.finance.dto.PaymentResponse;
import com.limoz.fleet.vehicle.VehicleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

@Component
@RequiredArgsConstructor
public class FinanceMapper {

    private final CustomerMapper customerMapper;
    private final ContractMapper contractMapper;
    private final VehicleMapper vehicleMapper;
    private final DriverMapper driverMapper;
    private final Clock clock;

    /** Full invoice with lines and payment history (detail screen). */
    public InvoiceResponse toResponse(Invoice i, List<Payment> payments) {
        List<InvoiceLineResponse> lines = i.getLines().stream().map(l -> toResponse(l, i.getDiscountPercent())).toList();
        return build(i, lines, payments == null ? null : payments.stream().map(this::toResponse).toList());
    }

    /** List row: totals and status without lines or payments. */
    public InvoiceResponse toSummaryResponse(Invoice i) {
        return build(i, null, null);
    }

    private InvoiceResponse build(Invoice i, List<InvoiceLineResponse> lines, List<PaymentResponse> payments) {
        boolean overdue = i.getStatus() == InvoiceStatus.OVERDUE
                || (i.getStatus().isPayable() && i.getDueDate().isBefore(LocalDate.now(clock)));
        return new InvoiceResponse(i.getId(), i.getInvoiceNumber(), customerMapper.toSummary(i.getCustomer()), i.getBookingId(),
                contractMapper.toSummary(i.getPurchaseOrder()), i.getIssueDate(), i.getDueDate(), i.getPaymentTerms(), i.getCurrency(),
                i.getSubtotal(), i.getDiscountPercent(), i.getDiscountAmount(), i.getTaxAmount(), i.getTotalAmount(), i.getAmountPaid(),
                i.getBalanceDue(), i.getStatus(), overdue, i.getSentAt(), i.getPaidAt(), i.getNotes(), lines, payments,
                i.getCreatedAt(), i.getUpdatedAt(), i.getCreatedBy());
    }

    public InvoiceLineResponse toResponse(InvoiceLine l, java.math.BigDecimal discountPercent) {
        return new InvoiceLineResponse(l.getId(), l.getBookingLineId(), l.getDeploymentVoucherId(), l.getDescription(), l.getQuantity(),
                l.getUnitPrice(), l.getTaxPercent(), InvoiceCalculator.lineTaxAmount(l.getLineTotal(), discountPercent, l.getTaxPercent()),
                l.getLineTotal(), l.getSortOrder());
    }

    public PaymentResponse toResponse(Payment p) {
        return new PaymentResponse(p.getId(), p.getPaymentNumber(), p.getDirection(), p.getCounterpartyName(),
                p.getCustomer() == null ? null : p.getCustomer().getId(), p.getCustomer() == null ? null : p.getCustomer().getName(),
                p.getInvoice() == null ? null : p.getInvoice().getId(), p.getInvoice() == null ? null : p.getInvoice().getInvoiceNumber(),
                p.getMaintenanceRecordId(), p.getExpense() == null ? null : p.getExpense().getId(),
                p.getExpense() == null ? null : p.getExpense().getExpenseNumber(), p.getTrafficFineId(), p.getMethod(), p.getAmount(),
                p.getCurrency(), p.getPaidAt(), p.getExternalReference(), p.getReceiptAttachmentId(), p.getRecordedByUserId(), p.getNotes(),
                p.isReversed(), p.getReversalReason(), p.getCreatedAt(), p.getCreatedBy());
    }

    public ExpenseResponse toResponse(Expense e) {
        return new ExpenseResponse(e.getId(), e.getExpenseNumber(), toResponse(e.getCategory()), e.getDescription(), e.getAmount(),
                e.getCurrency(), e.getIncurredOn(), vehicleMapper.toSummary(e.getVehicle()), driverMapper.toSummary(e.getDriver()),
                e.getTripId(), e.getBookingId(), e.getSubmittedByUserId(), e.getSubmittedByName(), e.getStatus(), e.getApprovedByUserId(),
                e.getApprovedAt(), e.getRejectionReason(), e.getReceiptAttachmentId(), e.getNotes(), e.getCreatedAt(), e.getUpdatedAt());
    }

    public ExpenseCategoryResponse toResponse(ExpenseCategory c) {
        return new ExpenseCategoryResponse(c.getId(), c.getCode(), c.getName(), c.isActive(), c.getSortOrder());
    }
}
