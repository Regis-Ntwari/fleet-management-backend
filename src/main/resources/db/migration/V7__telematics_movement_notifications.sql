-- =====================================================================
-- V7: GPS positions, daily movement analysis, notifications, alert centre
-- =====================================================================

CREATE TABLE vehicle_positions (
    id               BIGSERIAL PRIMARY KEY,
    vehicle_id       BIGINT       NOT NULL REFERENCES vehicles (id) ON DELETE CASCADE,
    device_id        BIGINT REFERENCES telematics_devices (id) ON DELETE SET NULL,
    recorded_at      TIMESTAMPTZ  NOT NULL,
    latitude         NUMERIC(9,6) NOT NULL,
    longitude        NUMERIC(9,6) NOT NULL,
    speed_kph        NUMERIC(6,1) NOT NULL DEFAULT 0 CHECK (speed_kph >= 0),
    heading          NUMERIC(5,1),
    odometer_km      NUMERIC(12,1) CHECK (odometer_km IS NULL OR odometer_km >= 0),
    ignition_on      BOOLEAN,
    battery_voltage  NUMERIC(5,2),
    fuel_level_litres NUMERIC(8,2),
    source           VARCHAR(20)  NOT NULL DEFAULT 'provider',
    CONSTRAINT ux_vehicle_position_time UNIQUE (vehicle_id, recorded_at)
);
CREATE INDEX ix_vehicle_positions_vehicle_time ON vehicle_positions (vehicle_id, recorded_at DESC);

-- One row per vehicle per operational day (Africa/Kigali), computed from positions and trips.
CREATE TABLE daily_movement_summaries (
    id                    BIGSERIAL PRIMARY KEY,
    vehicle_id            BIGINT       NOT NULL REFERENCES vehicles (id) ON DELETE CASCADE,
    summary_date          DATE         NOT NULL,
    distance_km           NUMERIC(10,1) NOT NULL DEFAULT 0 CHECK (distance_km >= 0),
    moved                 BOOLEAN      NOT NULL DEFAULT FALSE,
    first_movement_at     TIMESTAMPTZ,
    last_movement_at      TIMESTAMPTZ,
    driving_minutes       INT          NOT NULL DEFAULT 0 CHECK (driving_minutes >= 0),
    idle_minutes          INT          NOT NULL DEFAULT 0 CHECK (idle_minutes >= 0),
    night_driving_minutes INT          NOT NULL DEFAULT 0 CHECK (night_driving_minutes >= 0),
    max_speed_kph         NUMERIC(6,1) NOT NULL DEFAULT 0,
    trips_count           INT          NOT NULL DEFAULT 0,
    start_latitude        NUMERIC(9,6),
    start_longitude       NUMERIC(9,6),
    end_latitude          NUMERIC(9,6),
    end_longitude         NUMERIC(9,6),
    start_location        VARCHAR(255),
    end_location          VARCHAR(255),
    gps_issue             BOOLEAN      NOT NULL DEFAULT FALSE,
    flags                 JSONB        NOT NULL DEFAULT '[]'::jsonb,
    data_source           VARCHAR(20)  NOT NULL DEFAULT 'TRIPS' CHECK (data_source IN ('TELEMATICS','TRIPS','MIXED','NONE')),
    computed_at           TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ux_daily_movement UNIQUE (vehicle_id, summary_date)
);
CREATE INDEX ix_daily_movement_date ON daily_movement_summaries (summary_date DESC);

CREATE TABLE notifications (
    id                BIGSERIAL PRIMARY KEY,
    recipient_user_id BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    notification_type VARCHAR(40)  NOT NULL,
    severity          VARCHAR(10)  NOT NULL DEFAULT 'INFO' CHECK (severity IN ('INFO','WARNING','CRITICAL')),
    title             VARCHAR(150) NOT NULL,
    message           VARCHAR(1000) NOT NULL,
    entity_type       VARCHAR(40),
    entity_id         BIGINT,
    link_path         VARCHAR(255),
    dedupe_key        VARCHAR(200),
    read_at           TIMESTAMPTZ,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX ix_notifications_recipient ON notifications (recipient_user_id, created_at DESC);
CREATE INDEX ix_notifications_unread ON notifications (recipient_user_id) WHERE read_at IS NULL;
CREATE UNIQUE INDEX ux_notifications_dedupe ON notifications (recipient_user_id, dedupe_key) WHERE dedupe_key IS NOT NULL;

-- Management alert centre: conditions detected by scans; auto-resolved when the condition clears.
CREATE TABLE alerts (
    id                  BIGSERIAL PRIMARY KEY,
    alert_type          VARCHAR(40)  NOT NULL,
    severity            VARCHAR(10)  NOT NULL CHECK (severity IN ('INFO','WARNING','CRITICAL')),
    title               VARCHAR(150) NOT NULL,
    message             VARCHAR(1000) NOT NULL,
    entity_type         VARCHAR(40)  NOT NULL,
    entity_id           BIGINT       NOT NULL,
    entity_reference    VARCHAR(120),
    link_path           VARCHAR(255),
    dedupe_key          VARCHAR(200) NOT NULL UNIQUE,
    status              VARCHAR(15)  NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','ACKNOWLEDGED','RESOLVED')),
    first_detected_at   TIMESTAMPTZ  NOT NULL,
    last_detected_at    TIMESTAMPTZ  NOT NULL,
    acknowledged_by_user_id BIGINT REFERENCES users (id) ON DELETE SET NULL,
    acknowledged_at     TIMESTAMPTZ,
    resolved_at         TIMESTAMPTZ,
    resolution_note     VARCHAR(255)
);
CREATE INDEX ix_alerts_status_severity ON alerts (status, severity);
CREATE INDEX ix_alerts_entity ON alerts (entity_type, entity_id);
