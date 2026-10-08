package com.limoz.fleet.telematics;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

/**
 * Provider used when no GPS platform is configured ({@code fleet.telematics.provider=none} or unset).
 * Positions can still be ingested through the REST/CSV endpoints; the sync job only refreshes GPS statuses.
 */
@Component
@ConditionalOnProperty(prefix = "fleet.telematics", name = "provider", havingValue = "none", matchIfMissing = true)
public class NoopTelematicsProvider implements TelematicsProvider {

    public static final String CODE = "none";

    @Override
    public String providerCode() {
        return CODE;
    }

    @Override
    public boolean isConfigured() {
        return false;
    }

    @Override
    public List<PositionSample> fetchLatestPositions(Collection<TelematicsDevice> devices) {
        return List.of();
    }

    @Override
    public List<PositionSample> fetchHistory(TelematicsDevice device, Instant from, Instant to) {
        return List.of();
    }
}
