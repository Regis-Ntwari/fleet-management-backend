package com.limoz.fleet.document.job;

import com.limoz.fleet.document.service.DocumentService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Nightly recalculation of document statuses so EXPIRING_SOON / EXPIRED flip without user action. */
@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentStatusJob {

    private final DocumentService documentService;

    @Scheduled(cron = "${fleet.jobs.document-status-cron}", zone = "${fleet.timezone}")
    public void refreshDocumentStatuses() {
        int changed = documentService.refreshStatuses();
        log.debug("Document status job finished, {} change(s)", changed);
    }
}
