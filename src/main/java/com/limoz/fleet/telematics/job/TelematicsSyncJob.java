package com.limoz.fleet.telematics.job;

import com.limoz.fleet.telematics.domain.TelematicsDevice;
import com.limoz.fleet.telematics.repository.TelematicsDeviceRepository;
import com.limoz.fleet.telematics.service.PositionIngestService;
import com.limoz.fleet.telematics.service.TelematicsDeviceService;
import com.limoz.fleet.telematics.service.TelematicsProvider;

import com.limoz.fleet.telematics.dto.IngestResult;
import com.limoz.fleet.telematics.dto.PositionInput;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Periodic provider poll: when a provider is configured, the latest position of every active device is fetched
 * and ingested; GPS statuses are refreshed on every run regardless of the provider.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TelematicsSyncJob {

    private final TelematicsProvider provider;
    private final TelematicsDeviceRepository deviceRepository;
    private final PositionIngestService ingestService;
    private final TelematicsDeviceService deviceService;

    @Scheduled(cron = "${fleet.telematics.sync-cron}", zone = "${fleet.timezone}")
    public void sync() {
        if (provider.isConfigured()) {
            try {
                pollProvider();
            } catch (RuntimeException ex) {
                log.error("Telematics provider {} sync failed: {}", provider.providerCode(), ex.getMessage(), ex);
            }
        }
        deviceService.refreshGpsStatuses();
    }

    private void pollProvider() {
        List<TelematicsDevice> devices = deviceRepository.findActiveWithExternalId();
        if (devices.isEmpty()) {
            return;
        }
        List<PositionInput> rows = provider.fetchLatestPositions(devices).stream()
                .map(s -> new PositionInput(null, null, s.externalDeviceId(), s.recordedAt(), s.latitude(), s.longitude(),
                        s.speedKph(), s.heading(), s.odometerKm(), s.ignitionOn(), s.batteryVoltage(), s.fuelLevelLitres()))
                .toList();
        if (rows.isEmpty()) {
            return;
        }
        IngestResult result = ingestService.ingest(rows, provider.providerCode());
        log.info("Telematics sync ({}): {} received, {} imported, {} duplicates, {} rejected", provider.providerCode(),
                result.received(), result.imported(), result.duplicates(), result.rejected());
    }
}
