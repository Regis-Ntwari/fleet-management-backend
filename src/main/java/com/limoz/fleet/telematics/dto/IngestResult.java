package com.limoz.fleet.telematics.dto;

import java.util.List;

/**
 * Outcome of a position batch. {@code received = imported + duplicates + rejected}; every rejected row is
 * listed in {@code errors} with its 1-based row number and the reason.
 */
public record IngestResult(int received, int imported, int duplicates, int rejected, List<RowError> errors) {

    public record RowError(int row, String reason) {}
}
