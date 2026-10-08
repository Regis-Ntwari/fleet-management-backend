package com.limoz.fleet.finance;

import com.limoz.fleet.common.api.PageResponse;
import com.limoz.fleet.finance.dto.InvoiceFilter;
import com.limoz.fleet.finance.dto.InvoiceFromBookingRequest;
import com.limoz.fleet.finance.dto.InvoiceRequest;
import com.limoz.fleet.finance.dto.InvoiceResponse;
import com.limoz.fleet.finance.dto.PaymentReversalRequest;
import com.limoz.fleet.finance.dto.ReadyToBillResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/invoices")
@RequiredArgsConstructor
@Tag(name = "Invoices", description = "Bills & invoices: draft, issue, track payments and overdue balances")
public class InvoiceController {

    private final InvoiceService invoiceService;

    @GetMapping
    @PreAuthorize("hasAuthority('FINANCE_READ')")
    @Operation(summary = "Search invoices (paginated)",
            description = "Example: `?q=INV-2026&customerId=3&status=ISSUED&status=OVERDUE&from=2026-01-01&overdue=true`")
    public PageResponse<InvoiceResponse> search(@ParameterObject InvoiceFilter filter,
                                                @ParameterObject @PageableDefault(size = 20, sort = "issueDate", direction = Sort.Direction.DESC) Pageable pageable) {
        return invoiceService.search(filter, pageable);
    }

    @GetMapping("/ready-to-bill")
    @PreAuthorize("hasAuthority('FINANCE_READ')")
    @Operation(summary = "Completed bookings waiting to be invoiced (the 'Ready for Billing' tab)")
    public List<ReadyToBillResponse> readyToBill() {
        return invoiceService.readyToBill();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('FINANCE_READ')")
    @Operation(summary = "Invoice detail with lines and payment history")
    public InvoiceResponse get(@PathVariable Long id) {
        return invoiceService.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('FINANCE_MANAGE')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a manual draft invoice")
    public InvoiceResponse create(@Valid @RequestBody InvoiceRequest request) {
        return invoiceService.create(request);
    }

    @PostMapping("/from-booking/{bookingId}")
    @PreAuthorize("hasAuthority('FINANCE_MANAGE')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Generate a draft invoice from a completed booking (lines pulled in automatically)")
    public InvoiceResponse createFromBooking(@PathVariable Long bookingId, @RequestBody(required = false) @Valid InvoiceFromBookingRequest request) {
        return invoiceService.createFromBooking(bookingId, request == null ? new InvoiceFromBookingRequest(null, null, null, null) : request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('FINANCE_MANAGE')")
    @Operation(summary = "Edit a draft invoice (lines, discount, terms)")
    public InvoiceResponse update(@PathVariable Long id, @Valid @RequestBody InvoiceRequest request) {
        return invoiceService.update(id, request);
    }

    @PostMapping("/{id}/issue")
    @PreAuthorize("hasAuthority('FINANCE_MANAGE')")
    @Operation(summary = "Issue (send) a draft invoice")
    public InvoiceResponse issue(@PathVariable Long id) {
        return invoiceService.issue(id);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('FINANCE_MANAGE')")
    @Operation(summary = "Cancel an unpaid invoice (payments must be reversed first)")
    public InvoiceResponse cancel(@PathVariable Long id, @RequestBody(required = false) @Valid PaymentReversalRequest request) {
        return invoiceService.cancel(id, request == null ? null : request.reason());
    }

    @PostMapping("/refresh-statuses")
    @PreAuthorize("hasAuthority('FINANCE_MANAGE')")
    @Operation(summary = "Flip invoices past their due date to OVERDUE (also runs nightly)")
    public int refreshStatuses() {
        return invoiceService.refreshStatuses();
    }
}
