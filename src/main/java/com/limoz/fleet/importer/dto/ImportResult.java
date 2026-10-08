package com.limoz.fleet.importer.dto;

import java.util.List;

/**
 * Outcome of a CSV/Excel import. Invalid rows are never silently accepted: every rejected row is reported with
 * its line number and the reason.
 */
public record ImportResult(
        String importType,
        String fileName,
        int totalRows,
        int imported,
        int updated,
        int rejected,
        int duplicates,
        List<RowError> errors) {

    public record RowError(int row, String reference, String reason) {}
}
