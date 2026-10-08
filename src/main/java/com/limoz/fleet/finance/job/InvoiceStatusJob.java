package com.limoz.fleet.finance.job;

import com.limoz.fleet.finance.domain.Invoice;
import com.limoz.fleet.finance.service.InvoiceService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Nightly flip of ISSUED / PARTIALLY_PAID invoices past their due date to OVERDUE (with notifications). */
@Slf4j
@Component
@RequiredArgsConstructor
public class InvoiceStatusJob {

    private final InvoiceService invoiceService;

    @Scheduled(cron = "${fleet.jobs.document-status-cron}", zone = "${fleet.timezone}")
    public void refreshInvoiceStatuses() {
        int changed = invoiceService.refreshStatuses();
        log.debug("Invoice status job finished, {} invoice(s) became overdue", changed);
    }
}
