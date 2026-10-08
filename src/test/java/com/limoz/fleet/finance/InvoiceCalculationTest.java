package com.limoz.fleet.finance;

import com.limoz.fleet.finance.domain.InvoiceCalculator;
import com.limoz.fleet.finance.domain.InvoiceStatus;
import com.limoz.fleet.finance.domain.PaymentTerms;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InvoiceCalculationTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 6, 4);

    @Test
    @DisplayName("subtotal, discount, tax and total follow the reference application's arithmetic")
    void totalsWithDiscount() {
        List<InvoiceCalculator.LineInput> lines = List.of(
                new InvoiceCalculator.LineInput(new BigDecimal("2"), new BigDecimal("100000"), new BigDecimal("18")),
                new InvoiceCalculator.LineInput(new BigDecimal("1"), new BigDecimal("50000"), new BigDecimal("18")));
        InvoiceCalculator.Totals totals = InvoiceCalculator.compute(lines, new BigDecimal("10"));

        assertEquals(new BigDecimal("250000.00"), totals.subtotal());
        assertEquals(new BigDecimal("25000.00"), totals.discountAmount());
        assertEquals(new BigDecimal("40500.00"), totals.taxAmount());
        assertEquals(new BigDecimal("265500.00"), totals.total());
        assertEquals(List.of(new BigDecimal("200000.00"), new BigDecimal("50000.00")), totals.lineTotals());
    }

    @Test
    @DisplayName("tax is applied per line so untaxed extras do not attract VAT")
    void perLineTax() {
        List<InvoiceCalculator.LineInput> lines = List.of(
                new InvoiceCalculator.LineInput(new BigDecimal("3"), new BigDecimal("1000"), new BigDecimal("18")),
                new InvoiceCalculator.LineInput(new BigDecimal("1"), new BigDecimal("500"), BigDecimal.ZERO));
        InvoiceCalculator.Totals totals = InvoiceCalculator.compute(lines, null);

        assertEquals(new BigDecimal("3500.00"), totals.subtotal());
        assertEquals(new BigDecimal("0.00"), totals.discountAmount());
        assertEquals(new BigDecimal("540.00"), totals.taxAmount());
        assertEquals(new BigDecimal("4040.00"), totals.total());
    }

    @Test
    @DisplayName("fractional quantities and prices are rounded half-up to cents")
    void rounding() {
        InvoiceCalculator.Totals totals = InvoiceCalculator.compute(
                List.of(new InvoiceCalculator.LineInput(new BigDecimal("1.5"), new BigDecimal("33.33"), new BigDecimal("18"))), BigDecimal.ZERO);
        assertEquals(new BigDecimal("50.00"), totals.subtotal());
        assertEquals(new BigDecimal("9.00"), totals.taxAmount());
        assertEquals(new BigDecimal("59.00"), totals.total());
    }

    @Test
    @DisplayName("due date = issue date + term days, DUE_ON_RECEIPT being the issue date itself")
    void dueDateFromTerms() {
        assertEquals(TODAY, InvoiceCalculator.dueDate(TODAY, PaymentTerms.DUE_ON_RECEIPT));
        assertEquals(TODAY.plusDays(7), InvoiceCalculator.dueDate(TODAY, PaymentTerms.NET_7));
        assertEquals(TODAY.plusDays(14), InvoiceCalculator.dueDate(TODAY, PaymentTerms.NET_14));
        assertEquals(TODAY.plusDays(30), InvoiceCalculator.dueDate(TODAY, PaymentTerms.NET_30));
        assertEquals(TODAY.plusDays(45), InvoiceCalculator.dueDate(TODAY, PaymentTerms.NET_45));
        assertEquals(TODAY.plusDays(60), InvoiceCalculator.dueDate(TODAY, PaymentTerms.NET_60));
        assertEquals(TODAY.plusDays(30), InvoiceCalculator.dueDate(TODAY, null));
        assertEquals(PaymentTerms.NET_30, PaymentTerms.forDays(30));
        assertEquals(PaymentTerms.NET_14, PaymentTerms.forDays(15));
        assertEquals(PaymentTerms.DUE_ON_RECEIPT, PaymentTerms.forDays(0));
    }

    @Test
    @DisplayName("status derives from the paid amount and the due date")
    void statusDerivation() {
        BigDecimal total = new BigDecimal("1000.00");
        LocalDate due = TODAY.plusDays(10);
        assertEquals(InvoiceStatus.ISSUED, InvoiceCalculator.deriveStatus(total, BigDecimal.ZERO, due, TODAY));
        assertEquals(InvoiceStatus.PARTIALLY_PAID, InvoiceCalculator.deriveStatus(total, new BigDecimal("400"), due, TODAY));
        assertEquals(InvoiceStatus.PAID, InvoiceCalculator.deriveStatus(total, new BigDecimal("1000.00"), due, TODAY));
        assertEquals(InvoiceStatus.PAID, InvoiceCalculator.deriveStatus(total, new BigDecimal("1000.00"), TODAY.minusDays(5), TODAY));
        assertEquals(InvoiceStatus.OVERDUE, InvoiceCalculator.deriveStatus(total, BigDecimal.ZERO, TODAY.minusDays(1), TODAY));
        assertEquals(InvoiceStatus.OVERDUE, InvoiceCalculator.deriveStatus(total, new BigDecimal("400"), TODAY.minusDays(1), TODAY));
        assertEquals(InvoiceStatus.ISSUED, InvoiceCalculator.deriveStatus(total, null, TODAY, TODAY), "due today is not yet overdue");
    }

    @Test
    @DisplayName("line tax after a proportional discount")
    void lineTaxAfterDiscount() {
        BigDecimal tax = InvoiceCalculator.lineTaxAmount(new BigDecimal("200000.00"), new BigDecimal("10"), new BigDecimal("18"));
        assertEquals(new BigDecimal("32400.00"), tax);
        assertTrue(InvoiceCalculator.lineTaxAmount(new BigDecimal("100.00"), null, null).signum() == 0);
    }
}
