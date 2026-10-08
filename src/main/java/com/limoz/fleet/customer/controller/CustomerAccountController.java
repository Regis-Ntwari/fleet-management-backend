package com.limoz.fleet.customer.controller;

import com.limoz.fleet.customer.service.CommitmentService;
import com.limoz.fleet.customer.service.CustomerAccountService;
import com.limoz.fleet.customer.service.PurchaseOrderService;

import com.limoz.fleet.customer.dto.CommitmentResponse;
import com.limoz.fleet.customer.dto.CustomerAccountSummary;
import com.limoz.fleet.customer.dto.PurchaseOrderResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Client profile sub-resources: contracts, LPOs and the account position. */
@RestController
@RequestMapping("/api/v1/customers/{id}")
@RequiredArgsConstructor
@Tag(name = "Clients", description = "Corporate accounts LIMOZ provides transport for")
public class CustomerAccountController {

    private final CommitmentService commitmentService;
    private final PurchaseOrderService purchaseOrderService;
    private final CustomerAccountService customerAccountService;

    @GetMapping("/commitments")
    @PreAuthorize("hasAuthority('CUSTOMER_READ')")
    @Operation(summary = "Commitments of a client")
    public List<CommitmentResponse> commitments(@PathVariable Long id) {
        return commitmentService.listForCustomer(id);
    }

    @GetMapping("/purchase-orders")
    @PreAuthorize("hasAuthority('CUSTOMER_READ')")
    @Operation(summary = "LPOs of a client")
    public List<PurchaseOrderResponse> purchaseOrders(@PathVariable Long id) {
        return purchaseOrderService.listForCustomer(id);
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('CUSTOMER_READ')")
    @Operation(summary = "Account position: active bookings, contracts, billed, paid and outstanding balance (Dr)")
    public CustomerAccountSummary summary(@PathVariable Long id) {
        return customerAccountService.summary(id);
    }
}
