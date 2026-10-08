package com.limoz.fleet.customer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Nightly recalculation of commitment (EXPIRING_SOON / CLOSED) and LPO (EXPIRED) statuses. */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContractStatusJob {

    private final CommitmentService commitmentService;
    private final PurchaseOrderService purchaseOrderService;

    @Scheduled(cron = "${fleet.jobs.document-status-cron}", zone = "${fleet.timezone}")
    public void refreshContractStatuses() {
        int commitments = commitmentService.refreshStatuses();
        int orders = purchaseOrderService.refreshStatuses();
        log.debug("Contract status job finished: {} commitment(s), {} LPO(s) changed", commitments, orders);
    }
}
