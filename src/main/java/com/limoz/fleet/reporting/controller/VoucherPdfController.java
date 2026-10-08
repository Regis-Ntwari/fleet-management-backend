package com.limoz.fleet.reporting.controller;

import com.limoz.fleet.reporting.service.VoucherPdfService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/vouchers")
@RequiredArgsConstructor
@Tag(name = "Deployment Vouchers")
public class VoucherPdfController {

    private final VoucherPdfService voucherPdfService;

    @GetMapping("/{id}/pdf")
    @PreAuthorize("hasAuthority('BOOKING_READ')")
    @Operation(summary = "Printable deployment voucher (PDF)")
    public ResponseEntity<byte[]> pdf(@PathVariable Long id) {
        byte[] pdf = voucherPdfService.render(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"voucher-" + id + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }
}
