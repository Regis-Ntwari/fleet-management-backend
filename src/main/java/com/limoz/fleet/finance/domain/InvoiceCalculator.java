package com.limoz.fleet.finance.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

/**
 * Pure invoice arithmetic (no Spring, unit-testable):
 * <ul>
 *   <li>line_total = quantity x unit price (2 dp)</li>
 *   <li>subtotal = sum of line totals</li>
 *   <li>discount_amount = subtotal x discount% / 100 - applied to the subtotal before tax</li>
 *   <li>tax_amount = sum over lines of (line_total - its proportional discount) x tax% / 100</li>
 *   <li>total = subtotal - discount + tax</li>
 *   <li>due_date = issue date + payment-term days</li>
 * </ul>
 */
public final class InvoiceCalculator {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private InvoiceCalculator() {}

    public record LineInput(BigDecimal quantity, BigDecimal unitPrice, BigDecimal taxPercent) {}

    public record Totals(BigDecimal subtotal, BigDecimal discountAmount, BigDecimal taxAmount, BigDecimal total, List<BigDecimal> lineTotals) {}

    public static BigDecimal lineTotal(BigDecimal quantity, BigDecimal unitPrice) {
        return quantity.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP);
    }

    public static Totals compute(List<LineInput> lines, BigDecimal discountPercent) {
        BigDecimal discount = discountPercent == null ? BigDecimal.ZERO : discountPercent;
        List<BigDecimal> lineTotals = lines.stream().map(l -> lineTotal(l.quantity(), l.unitPrice())).toList();
        BigDecimal subtotal = lineTotals.stream().reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
        BigDecimal discountAmount = subtotal.multiply(discount).divide(HUNDRED, 2, RoundingMode.HALF_UP);
        BigDecimal discountFactor = BigDecimal.ONE.subtract(discount.divide(HUNDRED, 6, RoundingMode.HALF_UP));
        BigDecimal tax = BigDecimal.ZERO;
        for (int i = 0; i < lines.size(); i++) {
            tax = tax.add(lineTax(lineTotals.get(i), discountFactor, lines.get(i).taxPercent()));
        }
        tax = tax.setScale(2, RoundingMode.HALF_UP);
        BigDecimal total = subtotal.subtract(discountAmount).add(tax).setScale(2, RoundingMode.HALF_UP);
        return new Totals(subtotal, discountAmount, tax, total, lineTotals);
    }

    /** Tax on one line after the invoice-level discount has been applied proportionally. */
    public static BigDecimal lineTaxAmount(BigDecimal lineTotal, BigDecimal discountPercent, BigDecimal taxPercent) {
        BigDecimal discount = discountPercent == null ? BigDecimal.ZERO : discountPercent;
        BigDecimal factor = BigDecimal.ONE.subtract(discount.divide(HUNDRED, 6, RoundingMode.HALF_UP));
        return lineTax(lineTotal, factor, taxPercent).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal lineTax(BigDecimal lineTotal, BigDecimal discountFactor, BigDecimal taxPercent) {
        BigDecimal rate = taxPercent == null ? BigDecimal.ZERO : taxPercent;
        return lineTotal.multiply(discountFactor).multiply(rate).divide(HUNDRED, 6, RoundingMode.HALF_UP);
    }

    public static LocalDate dueDate(LocalDate issueDate, PaymentTerms terms) {
        return issueDate.plusDays(terms == null ? PaymentTerms.NET_30.days() : terms.days());
    }

    /**
     * Status of an issued invoice from its balance and due date: PAID once the paid amount covers the total,
     * OVERDUE when the due date has passed with a balance outstanding, PARTIALLY_PAID when something has
     * been received, otherwise ISSUED.
     */
    public static InvoiceStatus deriveStatus(BigDecimal total, BigDecimal amountPaid, LocalDate dueDate, LocalDate today) {
        BigDecimal paid = amountPaid == null ? BigDecimal.ZERO : amountPaid;
        if (total.signum() > 0 && paid.compareTo(total) >= 0) {
            return InvoiceStatus.PAID;
        }
        if (dueDate != null && dueDate.isBefore(today)) {
            return InvoiceStatus.OVERDUE;
        }
        if (paid.signum() > 0) {
            return InvoiceStatus.PARTIALLY_PAID;
        }
        return InvoiceStatus.ISSUED;
    }
}
