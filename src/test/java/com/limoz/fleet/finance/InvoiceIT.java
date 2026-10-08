package com.limoz.fleet.finance;

import com.limoz.fleet.finance.domain.PaymentDirection;
import com.limoz.fleet.finance.domain.PaymentMethod;
import com.limoz.fleet.finance.domain.PaymentTerms;

import com.limoz.fleet.customer.CustomerTestData;
import com.limoz.fleet.customer.dto.CustomerResponse;
import com.limoz.fleet.finance.dto.InvoiceFromBookingRequest;
import com.limoz.fleet.finance.dto.InvoiceLineRequest;
import com.limoz.fleet.finance.dto.InvoiceRequest;
import com.limoz.fleet.finance.dto.PaymentRequest;
import com.limoz.fleet.finance.dto.PaymentReversalRequest;
import com.limoz.fleet.security.Roles;
import com.limoz.fleet.support.AbstractIntegrationTest;
import com.limoz.fleet.support.TestData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InvoiceIT extends AbstractIntegrationTest {

    private static final AtomicInteger BOOKING_SEQ = new AtomicInteger(9000);

    @Autowired
    CustomerTestData customers;

    @Autowired
    TestData data;

    @Autowired
    JdbcClient jdbcClient;

    private InvoiceRequest invoiceRequest(Long customerId) {
        return new InvoiceRequest(customerId, null, null, LocalDate.now(), null, PaymentTerms.NET_30, "RWF", new BigDecimal("10"),
                List.of(new InvoiceLineRequest("Coaster x 2 - FULL_DAY", new BigDecimal("2"), new BigDecimal("100000"), new BigDecimal("18")),
                        new InvoiceLineRequest("Airport transfer", BigDecimal.ONE, new BigDecimal("50000"), new BigDecimal("18"))),
                null);
    }

    private long createInvoice(Long customerId) throws Exception {
        String body = mockMvc.perform(post("/api/v1/invoices").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(invoiceRequest(customerId))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asLong();
    }

    private long createIssuedInvoice(Long customerId) throws Exception {
        long id = createInvoice(customerId);
        mockMvc.perform(post("/api/v1/invoices/" + id + "/issue").header("Authorization", adminToken)).andExpect(status().isOk());
        return id;
    }

    private PaymentRequest payment(long invoiceId, String amount) {
        return new PaymentRequest(PaymentDirection.IN, null, null, invoiceId, null, null, null, PaymentMethod.BANK_TRANSFER,
                new BigDecimal(amount), "RWF", null, "BK-TRF-1", null, null);
    }

    @Test
    @DisplayName("a manual invoice computes subtotal, discount, VAT, total and the due date from the terms")
    void createManualInvoice() throws Exception {
        CustomerResponse customer = customers.customer();
        mockMvc.perform(post("/api/v1/invoices").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(invoiceRequest(customer.id()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.invoiceNumber").value(startsWith("INV-")))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.subtotal").value(250000.00))
                .andExpect(jsonPath("$.discountAmount").value(25000.00))
                .andExpect(jsonPath("$.taxAmount").value(40500.00))
                .andExpect(jsonPath("$.totalAmount").value(265500.00))
                .andExpect(jsonPath("$.balanceDue").value(265500.00))
                .andExpect(jsonPath("$.dueDate").value(LocalDate.now().plusDays(30).toString()))
                .andExpect(jsonPath("$.lines.length()").value(2))
                .andExpect(jsonPath("$.lines[0].lineTotal").value(200000.00))
                .andExpect(jsonPath("$.customer.name").value(customer.name()));
    }

    @Test
    @DisplayName("issue, partial payment, overpayment rejection and full payment drive the invoice status")
    void paymentsDriveStatus() throws Exception {
        CustomerResponse customer = customers.customer();
        long id = createInvoice(customer.id());

        mockMvc.perform(post("/api/v1/payments").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(payment(id, "1000"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVOICE_NOT_PAYABLE"));

        mockMvc.perform(post("/api/v1/invoices/" + id + "/issue").header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ISSUED"))
                .andExpect(jsonPath("$.sentAt").exists());

        mockMvc.perform(post("/api/v1/payments").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(payment(id, "100000"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.direction").value("IN"))
                .andExpect(jsonPath("$.counterpartyName").value(customer.name()))
                .andExpect(jsonPath("$.customerId").value(customer.id()));
        mockMvc.perform(get("/api/v1/invoices/" + id).header("Authorization", adminToken))
                .andExpect(jsonPath("$.status").value("PARTIALLY_PAID"))
                .andExpect(jsonPath("$.amountPaid").value(100000.00))
                .andExpect(jsonPath("$.balanceDue").value(165500.00))
                .andExpect(jsonPath("$.payments.length()").value(1));

        mockMvc.perform(post("/api/v1/payments").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(payment(id, "200000"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("OVERPAYMENT"));

        mockMvc.perform(post("/api/v1/payments").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(payment(id, "165500"))))
                .andExpect(status().isCreated());
        mockMvc.perform(get("/api/v1/invoices/" + id).header("Authorization", adminToken))
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.paidAt").exists())
                .andExpect(jsonPath("$.balanceDue").value(0));

        mockMvc.perform(post("/api/v1/invoices/" + id + "/cancel").header("Authorization", adminToken))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVOICE_PAID"));

        mockMvc.perform(get("/api/v1/customers/" + customer.id() + "/summary").header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalBilled").value(265500.00))
                .andExpect(jsonPath("$.totalPaid").value(265500.00))
                .andExpect(jsonPath("$.outstandingBalance").value(0));
    }

    @Test
    @DisplayName("cancelling requires live payments to be reversed first; reversal recomputes the invoice")
    void cancelRules() throws Exception {
        CustomerResponse customer = customers.customer();
        long draft = createInvoice(customer.id());
        mockMvc.perform(post("/api/v1/invoices/" + draft + "/cancel").header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        long issued = createIssuedInvoice(customer.id());
        String paid = mockMvc.perform(post("/api/v1/payments").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(payment(issued, "50000"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long paymentId = json.readTree(paid).get("id").asLong();

        mockMvc.perform(post("/api/v1/invoices/" + issued + "/cancel").header("Authorization", adminToken))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVOICE_HAS_PAYMENTS"));

        mockMvc.perform(post("/api/v1/payments/" + paymentId + "/reverse").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(new PaymentReversalRequest("Bounced transfer"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reversed").value(true));
        mockMvc.perform(get("/api/v1/invoices/" + issued).header("Authorization", adminToken))
                .andExpect(jsonPath("$.status").value("ISSUED"))
                .andExpect(jsonPath("$.amountPaid").value(0));

        mockMvc.perform(post("/api/v1/invoices/" + issued + "/cancel").header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        mockMvc.perform(post("/api/v1/invoices/" + issued + "/issue").header("Authorization", adminToken))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
    }

    @Test
    @DisplayName("an invoice is generated from a completed booking with its lines and extras, exactly once")
    void generateFromBooking() throws Exception {
        CustomerResponse customer = customers.customer();
        String number = "BK-2026-" + BOOKING_SEQ.incrementAndGet();
        long bookingId = jdbcClient.sql("insert into bookings (booking_number, customer_id, service_type, start_date, end_date, currency, total_amount, status, completed_at) "
                        + "values (:number, :customerId, 'CHARTER', :start, :end, 'RWF', 330000, 'READY_FOR_BILLING', now()) returning id")
                .param("number", number).param("customerId", customer.id())
                .param("start", LocalDate.of(2026, 6, 1)).param("end", LocalDate.of(2026, 6, 2))
                .query(Long.class).single();
        jdbcClient.sql("insert into booking_lines (booking_id, category_id, quantity, pricing_type, start_date, end_date, unit_price, line_total) "
                        + "values (:bookingId, :categoryId, 2, 'FULL_DAY', :start, :end, 150000, 300000)")
                .param("bookingId", bookingId).param("categoryId", data.anyCategoryId())
                .param("start", LocalDate.of(2026, 6, 1)).param("end", LocalDate.of(2026, 6, 2)).update();
        jdbcClient.sql("insert into booking_extra_charges (booking_id, description, amount) values (:bookingId, 'Fuel surcharge', 30000)")
                .param("bookingId", bookingId).update();

        mockMvc.perform(get("/api/v1/invoices/ready-to-bill").header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].bookingNumber").value(hasItem(number)));

        mockMvc.perform(post("/api/v1/invoices/from-booking/" + bookingId).header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new InvoiceFromBookingRequest(PaymentTerms.NET_14, BigDecimal.ZERO, LocalDate.of(2026, 6, 4), null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.bookingId").value(bookingId))
                .andExpect(jsonPath("$.dueDate").value("2026-06-18"))
                .andExpect(jsonPath("$.lines.length()").value(2))
                .andExpect(jsonPath("$.lines[0].description").value(startsWith("Minibus")))
                .andExpect(jsonPath("$.lines[0].quantity").value(2.0))
                .andExpect(jsonPath("$.lines[0].unitPrice").value(150000.00))
                .andExpect(jsonPath("$.lines[0].taxPercent").value(18.0))
                .andExpect(jsonPath("$.lines[1].description").value("Fuel surcharge"))
                .andExpect(jsonPath("$.subtotal").value(330000.00))
                .andExpect(jsonPath("$.taxAmount").value(59400.00))
                .andExpect(jsonPath("$.totalAmount").value(389400.00));

        mockMvc.perform(post("/api/v1/invoices/from-booking/" + bookingId).header("Authorization", adminToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE"));
        mockMvc.perform(get("/api/v1/invoices").header("Authorization", adminToken).param("bookingId", String.valueOf(bookingId)))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("viewers can read invoices and finance summaries but not create them")
    void authorizationAndSummary() throws Exception {
        CustomerResponse customer = customers.customer();
        String viewer = tokenFor(Roles.VIEWER);
        mockMvc.perform(get("/api/v1/invoices").header("Authorization", viewer).param("q", "INV").param("status", "DRAFT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(greaterThanOrEqualTo(0)));
        mockMvc.perform(get("/api/v1/finance/summary").header("Authorization", viewer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("RWF"));
        mockMvc.perform(post("/api/v1/invoices").header("Authorization", viewer)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(invoiceRequest(customer.id()))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mockMvc.perform(post("/api/v1/invoices/refresh-statuses").header("Authorization", adminToken))
                .andExpect(status().isOk());
    }
}
