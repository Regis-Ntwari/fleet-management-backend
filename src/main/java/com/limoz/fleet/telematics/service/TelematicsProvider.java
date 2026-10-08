package com.limoz.fleet.telematics.service;

import com.limoz.fleet.telematics.domain.PositionSample;
import com.limoz.fleet.telematics.domain.TelematicsDevice;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

/**
 * Abstraction over an external GPS/telematics platform (Wialon, Teltonika, ...). Exactly one implementation is
 * active, selected by the {@code fleet.telematics.provider} property; {@link NoopTelematicsProvider} is used when
 * no platform is configured. See {@code docs/telematics-integration.md} for how to add a real provider.
 */
public interface TelematicsProvider {

    /** Short code stored in {@code telematics_devices.provider_code} and {@code vehicle_positions.source}. */
    String providerCode();

    /** True when credentials/endpoints are present and the provider can be polled. */
    boolean isConfigured();

    /** Latest known position of each given device (devices the provider does not know are simply absent). */
    List<PositionSample> fetchLatestPositions(Collection<TelematicsDevice> devices);

    /** Position history of one device in the given window, ordered by time. */
    List<PositionSample> fetchHistory(TelematicsDevice device, Instant from, Instant to);
}
