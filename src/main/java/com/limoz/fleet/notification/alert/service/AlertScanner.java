package com.limoz.fleet.notification.alert.service;

import com.limoz.fleet.notification.alert.domain.AlertCandidate;

import java.util.List;

/**
 * Detects alert conditions in one module. Every Spring bean implementing this interface is invoked by
 * {@link AlertService#runScan()} on each scheduled or manual scan; a module adds alerts simply by providing a
 * {@code @Component} implementation (no registration needed). Implementations must be read-only, fast, and return
 * every condition that currently holds - conditions missing from the result are treated as cleared.
 */
public interface AlertScanner {

    /** Name used in logs and scan results. */
    default String scannerName() {
        return getClass().getSimpleName();
    }

    List<AlertCandidate> scan();
}
