# Telematics integration guide

The backend tracks vehicles through the `telematics` module without depending on any particular GPS platform.
This document explains the moving parts and how to connect a real provider such as Wialon.

## 1. Building blocks

| Piece | Role |
|---|---|
| `telematics_devices` (V3) | One tracker per vehicle: `provider_code`, `external_device_id` (the unit id on the provider side), SIM, GPS / fuel sensor health, last known fix. |
| `vehicle_positions` (V7) | Append-only time series of fixes, unique per vehicle and timestamp. |
| `TelematicsProvider` | The provider abstraction (`providerCode()`, `isConfigured()`, `fetchLatestPositions(devices)`, `fetchHistory(device, from, to)`). Exactly one bean is active. |
| `NoopTelematicsProvider` | Default bean when `fleet.telematics.provider=none` (or unset). Returns nothing and reports `isConfigured() == false`. |
| `PositionIngestService` | Single entry point for positions from any source (provider sync, `POST /api/v1/telematics/positions`, CSV import). Validates row by row, skips duplicates, updates the device's last-known fields and journals odometer readings with source `TELEMATICS`. |
| `TelematicsDeviceService.refreshGpsStatuses()` | Derives ONLINE / OFFLINE / NO_SIGNAL / DISCONNECTED from `last_communication_at` and the `telematics.gps_offline_minutes` setting; publishes a `GPS_OFFLINE` event when a device goes offline. |
| `TelematicsSyncJob` | Runs on `fleet.telematics.sync-cron` (default every 5 minutes): polls the provider when it is configured, then refreshes GPS statuses. |
| `DailyMovementService` / `DailyMovementJob` | Nightly movement analysis (distance, driving / idle / night minutes, flags) from positions, falling back to trip records. |

Until a provider is connected, positions can be fed through the REST batch endpoint or the CSV import, which
is also how historical exports from a provider can be back-filled.

## 2. Adding a provider (example: Wialon)

1. **Configuration.** Add the provider's settings to `AppProperties.Telematics` *only if* they are deployment
   configuration (base URL, API token, request timeout). Thresholds that operations staff may tune belong in
   `system_settings`. Suggested properties:

   ```yaml
   fleet:
     telematics:
       provider: wialon            # selects the bean, see step 2
       sync-cron: "0 */5 * * * *"  # polling cadence
       wialon:
         base-url: https://hst-api.wialon.com
         token: ${WIALON_TOKEN}
         timeout: PT10S
   ```

2. **Implement `TelematicsProvider`** in `com.limoz.fleet.telematics.wialon.WialonTelematicsProvider`:

   ```java
   @Component
   @ConditionalOnProperty(prefix = "fleet.telematics", name = "provider", havingValue = "wialon")
   public class WialonTelematicsProvider implements TelematicsProvider {
       public String providerCode() { return "wialon"; }
       public boolean isConfigured() { /* token and base URL present */ }
       public List<PositionSample> fetchLatestPositions(Collection<TelematicsDevice> devices) { ... }
       public List<PositionSample> fetchHistory(TelematicsDevice device, Instant from, Instant to) { ... }
   }
   ```

   Bean selection is purely by `fleet.telematics.provider`: `NoopTelematicsProvider` is conditional on the value
   `none` (match-if-missing), the Wialon bean on `wialon`. Nothing else in the codebase changes.

3. **Map unit identifiers.** Register each tracker with `POST /api/v1/telematics/devices` using
   `providerCode = "wialon"` and `externalDeviceId` = the Wialon unit id (`item.id` from `core/search_items`,
   or the unit's unique id / IMEI if that is what the API returns in messages). `fetchLatestPositions` receives
   the active devices of the provider and must return one `PositionSample` per unit it knows, with
   `externalDeviceId` set to the same identifier; the ingest service resolves the vehicle from it. Units unknown
   to the provider are simply omitted; samples for identifiers not registered here are rejected and reported.

4. **Map the payload** to `PositionSample`:

   | PositionSample | Wialon (`pos` block of `core/search_items` with flags 0x401, or `messages/load_interval`) |
   |---|---|
   | `recordedAt` | `pos.t` (unix seconds, UTC) |
   | `latitude` / `longitude` | `pos.y` / `pos.x` |
   | `speedKph` | `pos.s` |
   | `heading` | `pos.c` |
   | `odometerKm` | `cnm` (mileage counter) or the `odometer` sensor value |
   | `ignitionOn` | ignition sensor (`sens` of type `engine operation`) |
   | `batteryVoltage` | external voltage sensor |
   | `fuelLevelLitres` | fuel level sensor (only when the device's fuel sensor is installed) |

   Leave a field `null` when the unit does not report it; the ingest service tolerates nulls except for
   timestamp and coordinates.

5. **Session handling and errors.** Keep the Wialon session (`token/login`) inside the provider; re-login on
   `error 1` (invalid session). Throw a runtime exception on transport errors - `TelematicsSyncJob` logs it and
   still refreshes GPS statuses, so an outage of the provider surfaces as devices turning OFFLINE and a
   `GPS_OFFLINE` alert rather than as silent data loss.

6. **Polling cadence.** `fleet.telematics.sync-cron` controls how often the latest positions are pulled. Every
   run calls `fetchLatestPositions` once for all active devices (prefer one batched API call). For denser
   history (e.g. one fix every 30 s) use `fetchHistory` from a dedicated back-fill job rather than shortening the
   cron below the provider's rate limit. Duplicates (same vehicle and timestamp) are skipped by the ingest
   service, so overlapping polls are safe.

7. **Tests.** Unit-test the payload mapping with recorded JSON fixtures; integration-test the provider against a
   stub HTTP server. Do not point tests at the live platform. Fake GPS data belongs in test fixtures only -
   production code must never synthesise positions.

## 3. Operational notes

* `GET /api/v1/telematics/devices/problems` lists devices that are OFFLINE / NO_SIGNAL / DISCONNECTED and
  faulty fuel sensors; the same conditions feed the alert centre (`GpsOfflineScanner`).
* `telematics.gps_offline_minutes` (settings) defines how long a device may stay silent before it is OFFLINE.
* `POST /api/v1/telematics/devices/refresh-status` and `POST /api/v1/movement/recompute?date=` run the
  scheduled logic on demand (permission `TELEMATICS_MANAGE`).
* Position history is served by `GET /api/v1/telematics/vehicles/{id}/positions?from&to` (max 31 days, capped
  at 5000 rows) and the live view by `GET /api/v1/telematics/latest`.
