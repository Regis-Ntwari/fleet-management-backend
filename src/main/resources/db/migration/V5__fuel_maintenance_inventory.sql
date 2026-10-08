-- =====================================================================
-- V5: fuel transactions, workshops, service catalogue, maintenance jobs
--     (unified MNT + garage intake flow), spare parts & stock, preventive schedules
-- =====================================================================

CREATE TABLE fuel_transactions (
    id                       BIGSERIAL PRIMARY KEY,
    vehicle_id               BIGINT        NOT NULL REFERENCES vehicles (id),
    driver_id                BIGINT REFERENCES drivers (id) ON DELETE SET NULL,
    booking_slot_id          BIGINT REFERENCES booking_slots (id) ON DELETE SET NULL,
    trip_id                  BIGINT REFERENCES trips (id) ON DELETE SET NULL,
    transaction_at           TIMESTAMPTZ   NOT NULL,
    station_name             VARCHAR(120)  NOT NULL,
    supplier_name            VARCHAR(120),
    fuel_type                VARCHAR(20)   NOT NULL CHECK (fuel_type IN ('DIESEL','PETROL','HYBRID','ELECTRIC','LPG','OTHER')),
    litres                   NUMERIC(10,2) NOT NULL CHECK (litres > 0),
    price_per_litre          NUMERIC(12,2) NOT NULL CHECK (price_per_litre >= 0),
    total_amount             NUMERIC(14,2) NOT NULL CHECK (total_amount >= 0),
    currency                 VARCHAR(3)    NOT NULL DEFAULT 'RWF',
    odometer_km              BIGINT        NOT NULL CHECK (odometer_km >= 0),
    previous_odometer_km     BIGINT CHECK (previous_odometer_km IS NULL OR previous_odometer_km >= 0),
    distance_since_last_km   NUMERIC(10,1) CHECK (distance_since_last_km IS NULL OR distance_since_last_km >= 0),
    consumption_l_per_100km  NUMERIC(8,2)  CHECK (consumption_l_per_100km IS NULL OR consumption_l_per_100km >= 0),
    km_per_litre             NUMERIC(8,2)  CHECK (km_per_litre IS NULL OR km_per_litre >= 0),
    sensor_detected_litres   NUMERIC(10,2) CHECK (sensor_detected_litres IS NULL OR sensor_detected_litres >= 0),
    variance_litres          NUMERIC(10,2),
    anomaly                  BOOLEAN       NOT NULL DEFAULT FALSE,
    anomaly_reason           VARCHAR(255),
    full_tank                BOOLEAN       NOT NULL DEFAULT TRUE,
    receipt_number           VARCHAR(60),
    receipt_attachment_id    BIGINT REFERENCES attachments (id) ON DELETE SET NULL,
    payment_method           VARCHAR(20) CHECK (payment_method IS NULL OR payment_method IN ('CASH','FUEL_CARD','MOBILE_MONEY','BANK_TRANSFER','CREDIT')),
    entered_by_user_id       BIGINT REFERENCES users (id) ON DELETE SET NULL,
    notes                    VARCHAR(500),
    archived                 BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at               TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    created_by               VARCHAR(150),
    updated_by               VARCHAR(150),
    version                  BIGINT        NOT NULL DEFAULT 0
);
CREATE INDEX ix_fuel_vehicle_time ON fuel_transactions (vehicle_id, transaction_at DESC);
CREATE INDEX ix_fuel_time ON fuel_transactions (transaction_at);
CREATE INDEX ix_fuel_driver ON fuel_transactions (driver_id);
CREATE UNIQUE INDEX ux_fuel_receipt ON fuel_transactions (vehicle_id, UPPER(receipt_number)) WHERE receipt_number IS NOT NULL AND archived = FALSE;

CREATE TABLE workshops (
    id            BIGSERIAL PRIMARY KEY,
    name          VARCHAR(120) NOT NULL UNIQUE,
    workshop_type VARCHAR(10)  NOT NULL CHECK (workshop_type IN ('INTERNAL','EXTERNAL')),
    contact_name  VARCHAR(120),
    phone         VARCHAR(30),
    email         VARCHAR(150),
    address       VARCHAR(255),
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by    VARCHAR(150),
    updated_by    VARCHAR(150),
    version       BIGINT       NOT NULL DEFAULT 0
);

-- Preventive maintenance catalogue (engine oil, filters, brakes...)
CREATE TABLE service_types (
    id                    BIGSERIAL PRIMARY KEY,
    code                  VARCHAR(40)  NOT NULL UNIQUE,
    name                  VARCHAR(100) NOT NULL,
    category              VARCHAR(20)  NOT NULL DEFAULT 'SERVICE' CHECK (category IN ('SERVICE','REPAIR','INSPECTION')),
    default_interval_km   INT CHECK (default_interval_km IS NULL OR default_interval_km > 0),
    default_interval_days INT CHECK (default_interval_days IS NULL OR default_interval_days > 0),
    active                BOOLEAN      NOT NULL DEFAULT TRUE,
    sort_order            INT          NOT NULL DEFAULT 0,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by            VARCHAR(150),
    updated_by            VARCHAR(150),
    version               BIGINT       NOT NULL DEFAULT 0
);

CREATE TABLE spare_parts (
    id             BIGSERIAL PRIMARY KEY,
    part_number    VARCHAR(40)   NOT NULL,
    name           VARCHAR(120)  NOT NULL,
    category       VARCHAR(60),
    unit           VARCHAR(20)   NOT NULL DEFAULT 'pcs',
    unit_cost      NUMERIC(14,2) NOT NULL DEFAULT 0 CHECK (unit_cost >= 0),
    supplier       VARCHAR(120),
    minimum_stock  INT           NOT NULL DEFAULT 0 CHECK (minimum_stock >= 0),
    current_stock  INT           NOT NULL DEFAULT 0 CHECK (current_stock >= 0),
    location       VARCHAR(80),
    active         BOOLEAN       NOT NULL DEFAULT TRUE,
    notes          VARCHAR(255),
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    created_by     VARCHAR(150),
    updated_by     VARCHAR(150),
    version        BIGINT        NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX ux_spare_parts_number ON spare_parts (UPPER(part_number));
CREATE INDEX ix_spare_parts_low_stock ON spare_parts (current_stock, minimum_stock) WHERE active = TRUE;

-- A maintenance job covers both the simple MNT flow and the garage intake -> review -> work progress -> gate pass flow.
CREATE TABLE maintenance_records (
    id                      BIGSERIAL PRIMARY KEY,
    maintenance_number      VARCHAR(20)  NOT NULL UNIQUE,
    intake_number           VARCHAR(30)  UNIQUE,
    vehicle_id              BIGINT       NOT NULL REFERENCES vehicles (id),
    reported_at             TIMESTAMPTZ  NOT NULL,
    reported_by_user_id     BIGINT REFERENCES users (id) ON DELETE SET NULL,
    customer_id             BIGINT REFERENCES customers (id) ON DELETE SET NULL,
    owner_name              VARCHAR(150),
    department              VARCHAR(80),
    driver_id               BIGINT REFERENCES drivers (id) ON DELETE SET NULL,
    driver_name             VARCHAR(120),
    driver_contact          VARCHAR(30),
    complaint               TEXT         NOT NULL,
    visible_condition       TEXT,
    maintenance_type        VARCHAR(20)  NOT NULL DEFAULT 'CORRECTIVE'
        CHECK (maintenance_type IN ('PREVENTIVE','CORRECTIVE','BOTH','INSPECTION','ACCIDENT_REPAIR','TYRE','OTHER')),
    priority                VARCHAR(10)  NOT NULL DEFAULT 'MEDIUM' CHECK (priority IN ('LOW','MEDIUM','HIGH','CRITICAL')),
    workshop_id             BIGINT REFERENCES workshops (id) ON DELETE SET NULL,
    technician_user_id      BIGINT REFERENCES users (id) ON DELETE SET NULL,
    technician_name         VARCHAR(120),
    manager_user_id         BIGINT REFERENCES users (id) ON DELETE SET NULL,
    incident_id             BIGINT,
    odometer_km             BIGINT CHECK (odometer_km IS NULL OR odometer_km >= 0),
    started_at              TIMESTAMPTZ,
    expected_completion_at  DATE,
    completed_at            TIMESTAMPTZ,
    released_at             TIMESTAMPTZ,
    gate_pass_number        VARCHAR(30),
    review_date             DATE,
    diagnosis               TEXT,
    observed_faults         TEXT,
    recommended_repair      TEXT,
    labour_notes            TEXT,
    service_performed       TEXT,
    labor_cost              NUMERIC(14,2) NOT NULL DEFAULT 0 CHECK (labor_cost >= 0),
    parts_cost              NUMERIC(14,2) NOT NULL DEFAULT 0 CHECK (parts_cost >= 0),
    other_cost              NUMERIC(14,2) NOT NULL DEFAULT 0 CHECK (other_cost >= 0),
    total_cost              NUMERIC(14,2) NOT NULL DEFAULT 0 CHECK (total_cost >= 0),
    amount_paid             NUMERIC(14,2) NOT NULL DEFAULT 0 CHECK (amount_paid >= 0),
    payment_status          VARCHAR(10)  NOT NULL DEFAULT 'UNPAID' CHECK (payment_status IN ('UNPAID','PARTIAL','PAID')),
    status                  VARCHAR(20)  NOT NULL DEFAULT 'REPORTED'
        CHECK (status IN ('REPORTED','INSPECTION','APPROVED','IN_PROGRESS','WAITING_FOR_PARTS','COMPLETED','RELEASED','CANCELLED')),
    approved_by_user_id     BIGINT REFERENCES users (id) ON DELETE SET NULL,
    approved_at             TIMESTAMPTZ,
    cancellation_reason     VARCHAR(255),
    comments                TEXT,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by              VARCHAR(150),
    updated_by              VARCHAR(150),
    version                 BIGINT       NOT NULL DEFAULT 0
);
CREATE INDEX ix_maintenance_vehicle ON maintenance_records (vehicle_id, reported_at DESC);
CREATE INDEX ix_maintenance_status ON maintenance_records (status);
CREATE INDEX ix_maintenance_reported ON maintenance_records (reported_at);
CREATE INDEX ix_maintenance_technician ON maintenance_records (technician_user_id);

CREATE TABLE maintenance_tasks (
    id                    BIGSERIAL PRIMARY KEY,
    maintenance_record_id BIGINT       NOT NULL REFERENCES maintenance_records (id) ON DELETE CASCADE,
    service_type_id       BIGINT REFERENCES service_types (id) ON DELETE SET NULL,
    description           VARCHAR(255) NOT NULL,
    status                VARCHAR(10)  NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','DONE','SKIPPED')),
    labor_hours           NUMERIC(6,2) CHECK (labor_hours IS NULL OR labor_hours >= 0),
    labor_cost            NUMERIC(14,2) NOT NULL DEFAULT 0 CHECK (labor_cost >= 0),
    completed_at          TIMESTAMPTZ,
    completed_by          VARCHAR(150),
    notes                 VARCHAR(255),
    sort_order            INT          NOT NULL DEFAULT 0
);
CREATE INDEX ix_maintenance_tasks_record ON maintenance_tasks (maintenance_record_id);

CREATE TABLE maintenance_parts (
    id                    BIGSERIAL PRIMARY KEY,
    maintenance_record_id BIGINT        NOT NULL REFERENCES maintenance_records (id) ON DELETE CASCADE,
    spare_part_id         BIGINT REFERENCES spare_parts (id) ON DELETE SET NULL,
    part_name             VARCHAR(120)  NOT NULL,
    part_number           VARCHAR(40),
    quantity              INT           NOT NULL CHECK (quantity > 0),
    unit_cost             NUMERIC(14,2) NOT NULL DEFAULT 0 CHECK (unit_cost >= 0),
    line_total            NUMERIC(14,2) NOT NULL DEFAULT 0 CHECK (line_total >= 0),
    status                VARCHAR(10)   NOT NULL DEFAULT 'REQUESTED' CHECK (status IN ('REQUESTED','APPROVED','REJECTED','ISSUED')),
    approved_by_user_id   BIGINT REFERENCES users (id) ON DELETE SET NULL,
    approved_at           TIMESTAMPTZ,
    rejection_reason      VARCHAR(255),
    stock_movement_id     BIGINT,
    created_at            TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    created_by            VARCHAR(150)
);
CREATE INDEX ix_maintenance_parts_record ON maintenance_parts (maintenance_record_id);

CREATE TABLE maintenance_comments (
    id                    BIGSERIAL PRIMARY KEY,
    maintenance_record_id BIGINT       NOT NULL REFERENCES maintenance_records (id) ON DELETE CASCADE,
    user_id               BIGINT REFERENCES users (id) ON DELETE SET NULL,
    author_name           VARCHAR(150) NOT NULL,
    author_role           VARCHAR(60),
    comment_type          VARCHAR(20)  NOT NULL DEFAULT 'NOTE' CHECK (comment_type IN ('NOTE','STATUS_CHANGE','PART_DECISION','SYSTEM')),
    from_status           VARCHAR(20),
    to_status             VARCHAR(20),
    body                  TEXT         NOT NULL,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX ix_maintenance_comments_record ON maintenance_comments (maintenance_record_id, created_at);

CREATE TABLE stock_movements (
    id                   BIGSERIAL PRIMARY KEY,
    spare_part_id        BIGINT        NOT NULL REFERENCES spare_parts (id),
    movement_type        VARCHAR(12)   NOT NULL CHECK (movement_type IN ('IN','OUT','ADJUSTMENT','RETURN')),
    quantity             INT           NOT NULL CHECK (quantity <> 0),
    unit_cost            NUMERIC(14,2) CHECK (unit_cost IS NULL OR unit_cost >= 0),
    balance_after        INT           NOT NULL CHECK (balance_after >= 0),
    reference_type       VARCHAR(30),
    reference_id         BIGINT,
    reference_number     VARCHAR(40),
    maintenance_record_id BIGINT REFERENCES maintenance_records (id) ON DELETE SET NULL,
    performed_by_user_id BIGINT REFERENCES users (id) ON DELETE SET NULL,
    performed_by_name    VARCHAR(150),
    moved_at             TIMESTAMPTZ   NOT NULL,
    notes                VARCHAR(255),
    created_at           TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    created_by           VARCHAR(150)
);
CREATE INDEX ix_stock_movements_part ON stock_movements (spare_part_id, moved_at DESC);
CREATE INDEX ix_stock_movements_time ON stock_movements (moved_at DESC);
ALTER TABLE maintenance_parts ADD CONSTRAINT fk_maintenance_parts_movement FOREIGN KEY (stock_movement_id) REFERENCES stock_movements (id) ON DELETE SET NULL;

-- Preventive maintenance schedule per vehicle and service type
CREATE TABLE maintenance_schedules (
    id                    BIGSERIAL PRIMARY KEY,
    vehicle_id            BIGINT      NOT NULL REFERENCES vehicles (id) ON DELETE CASCADE,
    service_type_id       BIGINT      NOT NULL REFERENCES service_types (id),
    interval_km           INT CHECK (interval_km IS NULL OR interval_km > 0),
    interval_days         INT CHECK (interval_days IS NULL OR interval_days > 0),
    last_service_odometer BIGINT CHECK (last_service_odometer IS NULL OR last_service_odometer >= 0),
    last_service_date     DATE,
    last_maintenance_record_id BIGINT REFERENCES maintenance_records (id) ON DELETE SET NULL,
    next_service_odometer BIGINT CHECK (next_service_odometer IS NULL OR next_service_odometer >= 0),
    next_service_date     DATE,
    status                VARCHAR(10) NOT NULL DEFAULT 'OK' CHECK (status IN ('OK','DUE_SOON','OVERDUE')),
    active                BOOLEAN     NOT NULL DEFAULT TRUE,
    notes                 VARCHAR(255),
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by            VARCHAR(150),
    updated_by            VARCHAR(150),
    version               BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ux_maintenance_schedule UNIQUE (vehicle_id, service_type_id),
    CONSTRAINT ck_schedule_has_interval CHECK (interval_km IS NOT NULL OR interval_days IS NOT NULL)
);
CREATE INDEX ix_maintenance_schedules_status ON maintenance_schedules (status) WHERE active = TRUE;

INSERT INTO service_types (code, name, category, default_interval_km, default_interval_days, sort_order) VALUES
 ('ENGINE_OIL','Engine oil change','SERVICE',5000,180,1),
 ('OIL_FILTER','Oil filter','SERVICE',5000,180,2),
 ('AIR_FILTER','Air filter','SERVICE',10000,365,3),
 ('FUEL_FILTER','Fuel filter','SERVICE',20000,365,4),
 ('BRAKE_INSPECTION','Brake inspection','INSPECTION',10000,180,5),
 ('TYRES','Tyre rotation / replacement','SERVICE',10000,180,6),
 ('TRANSMISSION','Transmission service','SERVICE',40000,730,7),
 ('GENERAL_SERVICE','General service','SERVICE',5000,180,8),
 ('BATTERY','Battery check / replacement','INSPECTION',NULL,365,9),
 ('COOLANT','Coolant flush','SERVICE',40000,730,10),
 ('BODY_REPAIR','Body repair','REPAIR',NULL,NULL,20),
 ('ELECTRICAL','Electrical repair','REPAIR',NULL,NULL,21),
 ('ENGINE_REPAIR','Engine repair','REPAIR',NULL,NULL,22),
 ('SUSPENSION','Suspension repair','REPAIR',NULL,NULL,23),
 ('AC_SERVICE','Air conditioning service','SERVICE',NULL,365,24);

INSERT INTO workshops (name, workshop_type, address) VALUES
 ('LIMOZ Internal Garage','INTERNAL','Nyabugogo, Kigali');
