package com.limoz.fleet.customer;

import com.limoz.fleet.customer.dto.CommitmentRequest;
import com.limoz.fleet.customer.dto.CommitmentResponse;
import com.limoz.fleet.customer.dto.CustomerRequest;
import com.limoz.fleet.customer.dto.CustomerResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;

/** Client / contract fixtures shared by the customer, incident and finance integration tests. */
@Component
@RequiredArgsConstructor
public class CustomerTestData {

    private static final AtomicInteger SEQ = new AtomicInteger(500);

    private final CustomerService customerService;
    private final CommitmentService commitmentService;

    public CustomerRequest customerRequest() {
        int n = SEQ.incrementAndGet();
        return new CustomerRequest("CL" + n, "Test Client " + n, CustomerType.CORPORATE, "1" + n + "00123", "Contact " + n,
                "client" + n + "@test.limoz.rw", "+2507880" + n, "KN 5 Rd", "Kigali", "Rwanda", null, new BigDecimal("5000000"), true, null);
    }

    public CustomerResponse customer() {
        return customerService.create(customerRequest());
    }

    public CommitmentRequest commitmentRequest(Long customerId) {
        LocalDate start = LocalDate.now().withDayOfMonth(1);
        return new CommitmentRequest(customerId, "Staff Shuttle " + SEQ.incrementAndGet(), start, start.plusYears(1).minusDays(1),
                new BigDecimal("12000000"), "RWF", null, null);
    }

    public CommitmentResponse activeCommitment(Long customerId) {
        CommitmentResponse created = commitmentService.create(commitmentRequest(customerId));
        return commitmentService.activate(created.id());
    }
}
