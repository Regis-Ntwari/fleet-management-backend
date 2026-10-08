-- =====================================================================
-- V3: fleet master data - categories, vehicles, odometer history, drivers,
--     documents/certificates, vehicle-driver assignments, telematics devices
-- =====================================================================

CREATE TABLE vehicle_categories (
    id          BIGSERIAL PRIMARY KEY,
    code        VARCHAR(30)  NOT NULL UNIQUE,
    name        VARCHAR(80)  NOT NULL UNIQUE,
    description VARCHAR(255),
    min_seats   INT CHECK (min_seats IS NULL OR min_seats >= 0),
    max_seats   INT CHECK (max_seats IS NULL OR max_seats >= 0),
    default_day_rate NUMERIC(14,2) CHECK (default_day_rate IS NULL OR default_day_rate >= 0),
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    sort_order  INT          NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by  VARCHAR(150),
    updated_by  VARCHAR(150),
    version     BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_vehicle_categories_seats CHECK (min_seats IS NULL OR max_seats IS NULL OR min_seats <= max_seats)
);

CREATE TABLE drivers (
    id                      BIGSERIAL PRIMARY KEY,
    driver_code             VARCHAR(20)  NOT NULL UNIQUE,
    first_name              VARCHAR(80)  NOT NULL,
    last_name               VARCHAR(80)  NOT NULL,
    phone                   VARCHAR(30),
    email                   VARCHAR(150),
    national_id             VARCHAR(30),
    license_number          VARCHAR(40)  NOT NULL,
    license_category        VARCHAR(20),
    license_issue_date      DATE,
    license_expiry_date     DATE,
    employment_status       VARCHAR(20)  NOT NULL DEFAULT 'FULL_TIME'
        CHECK (employment_status IN ('FULL_TIME','CONTRACT','CASUAL','TERMINATED')),
    status                  VARCHAR(20)  NOT NULL DEFAULT 'AVAILABLE'
        CHECK (status IN ('AVAILABLE','ASSIGNED','ON_TRIP','OFF_DUTY','ON_LEAVE','SUSPENDED','INACTIVE')),
    current_vehicle_id      BIGINT,
    based_in                VARCHAR(80),
    emergency_contact_name  VARCHAR(120),
    emergency_contact_phone VARCHAR(30),
    joining_date            DATE,
    date_of_birth           DATE,
    photo_attachment_id     BIGINT REFERENCES attachments (id) ON DELETE SET NULL,
    notes                   TEXT,
    archived                BOOLEAN      NOT NULL DEFAULT FALSE,
    archived_at             TIMESTAMPTZ,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by              VARCHAR(150),
    updated_by              VARCHAR(150),
    version                 BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_drivers_license_dates CHECK (license_issue_date IS NULL OR license_expiry_date IS NULL OR license_issue_date <= license_expiry_date)
);
CREATE UNIQUE INDEX ux_drivers_license ON drivers (UPPER(license_number));
CREATE UNIQUE INDEX ux_drivers_national_id ON drivers (national_id) WHERE national_id IS NOT NULL;
CREATE INDEX ix_drivers_status ON drivers (status) WHERE archived = FALSE;
CREATE INDEX ix_drivers_name ON drivers (last_name, first_name);
CREATE INDEX ix_drivers_license_expiry ON drivers (license_expiry_date);

CREATE TABLE vehicles (
    id                       BIGSERIAL PRIMARY KEY,
    plate_number             VARCHAR(20)  NOT NULL,
    fleet_number             VARCHAR(20),
    make                     VARCHAR(60)  NOT NULL,
    model                    VARCHAR(80)  NOT NULL,
    model_year               INT CHECK (model_year IS NULL OR (model_year >= 1950 AND model_year <= 2100)),
    category_id              BIGINT       NOT NULL REFERENCES vehicle_categories (id),
    body_type                VARCHAR(40),
    fuel_type                VARCHAR(20)  NOT NULL DEFAULT 'DIESEL'
        CHECK (fuel_type IN ('DIESEL','PETROL','HYBRID','ELECTRIC','LPG','OTHER')),
    transmission             VARCHAR(20)  CHECK (transmission IS NULL OR transmission IN ('MANUAL','AUTOMATIC','SEMI_AUTOMATIC')),
    engine_number            VARCHAR(60),
    chassis_number           VARCHAR(60),
    color                    VARCHAR(40),
    odometer_km              BIGINT       NOT NULL DEFAULT 0 CHECK (odometer_km >= 0),
    seating_capacity         INT CHECK (seating_capacity IS NULL OR seating_capacity >= 0),
    purchase_date            DATE,
    acquisition_cost         NUMERIC(16,2) CHECK (acquisition_cost IS NULL OR acquisition_cost >= 0),
    ownership_type           VARCHAR(20)  NOT NULL DEFAULT 'OWNED'
        CHECK (ownership_type IN ('OWNED','LEASED','THIRD_PARTY')),
    owner_name               VARCHAR(150),
    owner_contact            VARCHAR(60),
    owner_driver_name        VARCHAR(120),
    insurance_provider       VARCHAR(120),
    insurance_policy_number  VARCHAR(80),
    insurance_expiry_date    DATE,
    day_rate                 NUMERIC(14,2) CHECK (day_rate IS NULL OR day_rate >= 0),
    current_driver_id        BIGINT REFERENCES drivers (id) ON DELETE SET NULL,
    operational_status       VARCHAR(20)  NOT NULL DEFAULT 'AVAILABLE'
        CHECK (operational_status IN ('AVAILABLE','ASSIGNED','ON_TRIP','RESERVED','IN_MAINTENANCE','OUT_OF_SERVICE','INACTIVE')),
    maintenance_status       VARCHAR(20)  NOT NULL DEFAULT 'OK'
        CHECK (maintenance_status IN ('OK','SERVICE_DUE_SOON','SERVICE_OVERDUE','IN_WORKSHOP')),
    department               VARCHAR(80),
    notes                    TEXT,
    archived                 BOOLEAN      NOT NULL DEFAULT FALSE,
    archived_at              TIMESTAMPTZ,
    created_at               TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by               VARCHAR(150),
    updated_by               VARCHAR(150),
    version                  BIGINT       NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX ux_vehicles_plate ON vehicles (UPPER(REPLACE(plate_number, ' ', '')));
CREATE UNIQUE INDEX ux_vehicles_fleet_number ON vehicles (UPPER(fleet_number)) WHERE fleet_number IS NOT NULL;
CREATE UNIQUE INDEX ux_vehicles_chassis ON vehicles (UPPER(chassis_number)) WHERE chassis_number IS NOT NULL;
CREATE INDEX ix_vehicles_status ON vehicles (operational_status) WHERE archived = FALSE;
CREATE INDEX ix_vehicles_category ON vehicles (category_id);
CREATE INDEX ix_vehicles_current_driver ON vehicles (current_driver_id);

ALTER TABLE drivers ADD CONSTRAINT fk_drivers_current_vehicle FOREIGN KEY (current_vehicle_id) REFERENCES vehicles (id) ON DELETE SET NULL;
ALTER TABLE users ADD CONSTRAINT fk_users_driver FOREIGN KEY (driver_id) REFERENCES drivers (id) ON DELETE SET NULL;

-- Every odometer change is journaled; readings may only decrease through an approved CORRECTION.
CREATE TABLE odometer_logs (
    id                BIGSERIAL PRIMARY KEY,
    vehicle_id        BIGINT      NOT NULL REFERENCES vehicles (id) ON DELETE CASCADE,
    reading_km        BIGINT      NOT NULL CHECK (reading_km >= 0),
    previous_km       BIGINT      CHECK (previous_km IS NULL OR previous_km >= 0),
    source            VARCHAR(20) NOT NULL
        CHECK (source IN ('MANUAL','TRIP','FUEL','MAINTENANCE','ASSIGNMENT','DEPLOYMENT','TELEMATICS','IMPORT','CORRECTION')),
    reference_type    VARCHAR(40),
    reference_id      BIGINT,
    correction_reason VARCHAR(255),
    recorded_at       TIMESTAMPTZ NOT NULL,
    recorded_by       VARCHAR(150)
);
CREATE INDEX ix_odometer_logs_vehicle ON odometer_logs (vehicle_id, recorded_at DESC);

CREATE TABLE document_types (
    id             BIGSERIAL PRIMARY KEY,
    code           VARCHAR(40)  NOT NULL UNIQUE,
    name           VARCHAR(100) NOT NULL,
    applies_to     VARCHAR(10)  NOT NULL CHECK (applies_to IN ('VEHICLE','DRIVER','BOTH')),
    required_for_dispatch BOOLEAN NOT NULL DEFAULT FALSE,
    warning_days   INT CHECK (warning_days IS NULL OR warning_days >= 0),
    active         BOOLEAN      NOT NULL DEFAULT TRUE,
    sort_order     INT          NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by     VARCHAR(150),
    updated_by     VARCHAR(150),
    version        BIGINT       NOT NULL DEFAULT 0
);

CREATE TABLE vehicle_documents (
    id               BIGSERIAL PRIMARY KEY,
    vehicle_id       BIGINT       NOT NULL REFERENCES vehicles (id) ON DELETE CASCADE,
    document_type_id BIGINT       NOT NULL REFERENCES document_types (id),
    document_number  VARCHAR(80),
    issuer           VARCHAR(120),
    issue_date       DATE,
    expiry_date      DATE,
    status           VARCHAR(20)  NOT NULL DEFAULT 'VALID' CHECK (status IN ('VALID','EXPIRING_SOON','EXPIRED','NOT_APPLICABLE')),
    attachment_id    BIGINT REFERENCES attachments (id) ON DELETE SET NULL,
    cost             NUMERIC(14,2) CHECK (cost IS NULL OR cost >= 0),
    notes            VARCHAR(500),
    superseded       BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by       VARCHAR(150),
    updated_by       VARCHAR(150),
    version          BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_vehicle_documents_dates CHECK (issue_date IS NULL OR expiry_date IS NULL OR issue_date <= expiry_date)
);
CREATE INDEX ix_vehicle_documents_vehicle ON vehicle_documents (vehicle_id);
CREATE INDEX ix_vehicle_documents_expiry ON vehicle_documents (expiry_date) WHERE superseded = FALSE;
CREATE INDEX ix_vehicle_documents_status ON vehicle_documents (status) WHERE superseded = FALSE;

CREATE TABLE driver_documents (
    id               BIGSERIAL PRIMARY KEY,
    driver_id        BIGINT       NOT NULL REFERENCES drivers (id) ON DELETE CASCADE,
    document_type_id BIGINT       NOT NULL REFERENCES document_types (id),
    document_number  VARCHAR(80),
    issuer           VARCHAR(120),
    issue_date       DATE,
    expiry_date      DATE,
    status           VARCHAR(20)  NOT NULL DEFAULT 'VALID' CHECK (status IN ('VALID','EXPIRING_SOON','EXPIRED','NOT_APPLICABLE')),
    attachment_id    BIGINT REFERENCES attachments (id) ON DELETE SET NULL,
    notes            VARCHAR(500),
    superseded       BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by       VARCHAR(150),
    updated_by       VARCHAR(150),
    version          BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_driver_documents_dates CHECK (issue_date IS NULL OR expiry_date IS NULL OR issue_date <= expiry_date)
);
CREATE INDEX ix_driver_documents_driver ON driver_documents (driver_id);
CREATE INDEX ix_driver_documents_expiry ON driver_documents (expiry_date) WHERE superseded = FALSE;

-- Historical vehicle <-> driver assignments. Never updated destructively: ending an assignment sets end_at/status.
CREATE TABLE vehicle_assignments (
    id                     BIGSERIAL PRIMARY KEY,
    vehicle_id             BIGINT       NOT NULL REFERENCES vehicles (id),
    driver_id              BIGINT       NOT NULL REFERENCES drivers (id),
    start_at               TIMESTAMPTZ  NOT NULL,
    end_at                 TIMESTAMPTZ,
    assigned_by_user_id    BIGINT REFERENCES users (id) ON DELETE SET NULL,
    ended_by_user_id       BIGINT REFERENCES users (id) ON DELETE SET NULL,
    purpose                VARCHAR(255),
    odometer_at_assignment BIGINT CHECK (odometer_at_assignment IS NULL OR odometer_at_assignment >= 0),
    odometer_at_return     BIGINT CHECK (odometer_at_return IS NULL OR odometer_at_return >= 0),
    status                 VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','COMPLETED','CANCELLED')),
    comments               VARCHAR(500),
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by             VARCHAR(150),
    updated_by             VARCHAR(150),
    version                BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_assignment_period CHECK (end_at IS NULL OR end_at >= start_at)
);
CREATE UNIQUE INDEX ux_assignment_active_vehicle ON vehicle_assignments (vehicle_id) WHERE status = 'ACTIVE';
CREATE UNIQUE INDEX ux_assignment_active_driver ON vehicle_assignments (driver_id) WHERE status = 'ACTIVE';
CREATE INDEX ix_assignments_vehicle_period ON vehicle_assignments (vehicle_id, start_at DESC);
CREATE INDEX ix_assignments_driver_period ON vehicle_assignments (driver_id, start_at DESC);

CREATE TABLE telematics_devices (
    id                    BIGSERIAL PRIMARY KEY,
    vehicle_id            BIGINT       NOT NULL UNIQUE REFERENCES vehicles (id) ON DELETE CASCADE,
    provider_code         VARCHAR(30)  NOT NULL DEFAULT 'manual',
    external_device_id    VARCHAR(80),
    sim_number            VARCHAR(30),
    installed_at          DATE,
    active                BOOLEAN      NOT NULL DEFAULT TRUE,
    gps_status            VARCHAR(20)  NOT NULL DEFAULT 'UNKNOWN'
        CHECK (gps_status IN ('ONLINE','OFFLINE','NO_SIGNAL','DISCONNECTED','UNKNOWN')),
    fuel_sensor_status    VARCHAR(20)  NOT NULL DEFAULT 'NOT_INSTALLED'
        CHECK (fuel_sensor_status IN ('OK','FAULTY','NOT_INSTALLED','UNKNOWN')),
    last_communication_at TIMESTAMPTZ,
    last_latitude         NUMERIC(9,6),
    last_longitude        NUMERIC(9,6),
    last_speed_kph        NUMERIC(6,1),
    last_odometer_km      BIGINT,
    last_ignition_on      BOOLEAN,
    last_battery_voltage  NUMERIC(5,2),
    notes                 VARCHAR(255),
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by            VARCHAR(150),
    updated_by            VARCHAR(150),
    version               BIGINT       NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX ux_telematics_external ON telematics_devices (provider_code, external_device_id) WHERE external_device_id IS NOT NULL;

-- ---------------------------------------------------------------------
-- reference data
-- ---------------------------------------------------------------------
INSERT INTO vehicle_categories (code, name, description, min_seats, max_seats, default_day_rate, sort_order) VALUES
 ('LUX_SUV','Luxury SUV','Executive SUV (Land Cruiser V8, Prado VX)',4,7,350000,1),
 ('SUV','SUV','Standard SUV / 4x4',4,7,180000,2),
 ('SEDAN','Sedan','Standard saloon car',4,5,90000,3),
 ('LUX_SEDAN','Luxury Sedan','Executive saloon',4,5,250000,4),
 ('LUX_VAN','Luxury Van','Executive van (Alphard, Hiace VIP)',6,9,220000,5),
 ('MINIBUS','Minibus','Toyota Hiace and similar',14,19,160000,6),
 ('COASTER','Coaster','Mid-size passenger bus',24,30,260000,7),
 ('COACH','Executive Bus','Long-distance large bus',40,60,450000,8),
 ('SAFARI','Safari Vehicle','Pop-up roof safari 4x4',4,7,200000,9),
 ('CARGO_VAN','Cargo Van','Freight and parcels',0,3,120000,10),
 ('TRUCK','Truck','General cargo truck',2,3,300000,11),
 ('FLATBED','Flatbed','Flatbed truck',2,3,320000,12),
 ('TANKER','Oil Tanker','Fuel/oil tanker',2,3,400000,13);

INSERT INTO document_types (code, name, applies_to, required_for_dispatch, sort_order) VALUES
 ('INSURANCE','Insurance','VEHICLE',TRUE,1),
 ('INSPECTION','Technical Inspection','VEHICLE',TRUE,2),
 ('ROAD_LICENCE','Road Licence','VEHICLE',TRUE,3),
 ('REGISTRATION','Registration (Carte Jaune)','VEHICLE',FALSE,4),
 ('RURA_PERMIT','RURA Transport Permit','VEHICLE',TRUE,5),
 ('DRIVING_LICENCE','Driving Licence','DRIVER',TRUE,10),
 ('MEDICAL_CERT','Medical Certificate','DRIVER',FALSE,11),
 ('PSV_PERMIT','PSV Driver Permit','DRIVER',FALSE,12),
 ('OTHER','Other','BOTH',FALSE,99);
