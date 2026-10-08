-- =====================================================================
-- V4: clients, commitments, LPOs, bookings (lines + deployment slots),
--     deployment vouchers, trips
-- =====================================================================

CREATE TABLE customers (
    id             BIGSERIAL PRIMARY KEY,
    customer_code  VARCHAR(20),
    name           VARCHAR(150) NOT NULL,
    customer_type  VARCHAR(20)  NOT NULL DEFAULT 'CORPORATE' CHECK (customer_type IN ('CORPORATE','GOVERNMENT','NGO','INDIVIDUAL')),
    tin            VARCHAR(30),
    contact_person VARCHAR(120),
    email          VARCHAR(150),
    phone          VARCHAR(30),
    address        VARCHAR(255),
    city           VARCHAR(80),
    country        VARCHAR(80) DEFAULT 'Rwanda',
    account_manager_user_id BIGINT REFERENCES users (id) ON DELETE SET NULL,
    credit_limit   NUMERIC(16,2) CHECK (credit_limit IS NULL OR credit_limit >= 0),
    active         BOOLEAN      NOT NULL DEFAULT TRUE,
    notes          TEXT,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by     VARCHAR(150),
    updated_by     VARCHAR(150),
    version        BIGINT       NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX ux_customers_name ON customers (LOWER(name));
CREATE UNIQUE INDEX ux_customers_tin ON customers (REPLACE(tin, ' ', '')) WHERE tin IS NOT NULL;
CREATE UNIQUE INDEX ux_customers_code ON customers (UPPER(customer_code)) WHERE customer_code IS NOT NULL;

-- Framework contracts that bookings and LPOs draw down against (CMT-0042)
CREATE TABLE commitments (
    id               BIGSERIAL PRIMARY KEY,
    reference        VARCHAR(20)  NOT NULL UNIQUE,
    customer_id      BIGINT       NOT NULL REFERENCES customers (id),
    title            VARCHAR(150) NOT NULL,
    period_start     DATE         NOT NULL,
    period_end       DATE         NOT NULL,
    contracted_value NUMERIC(16,2) NOT NULL CHECK (contracted_value >= 0),
    currency         VARCHAR(3)   NOT NULL DEFAULT 'RWF',
    status           VARCHAR(20)  NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT','ACTIVE','EXPIRING_SOON','CLOSED','CANCELLED')),
    attachment_id    BIGINT REFERENCES attachments (id) ON DELETE SET NULL,
    notes            TEXT,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by       VARCHAR(150),
    updated_by       VARCHAR(150),
    version          BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_commitments_period CHECK (period_start <= period_end)
);
CREATE INDEX ix_commitments_customer ON commitments (customer_id);

CREATE TABLE bookings (
    id               BIGSERIAL PRIMARY KEY,
    booking_number   VARCHAR(20)  NOT NULL UNIQUE,
    customer_id      BIGINT       NOT NULL REFERENCES customers (id),
    commitment_id    BIGINT REFERENCES commitments (id) ON DELETE SET NULL,
    contact_name     VARCHAR(120),
    contact_phone    VARCHAR(30),
    contact_email    VARCHAR(150),
    service_type     VARCHAR(30)  NOT NULL DEFAULT 'CHARTER'
        CHECK (service_type IN ('CHARTER','AIRPORT_TRANSFER','STAFF_SHUTTLE','FIELD_TRIP','EVENT','CARGO','SAFARI','OTHER')),
    pickup_location  VARCHAR(255),
    dropoff_location VARCHAR(255),
    start_date       DATE         NOT NULL,
    end_date         DATE         NOT NULL,
    pickup_time      TIME,
    return_time      TIME,
    passengers       INT CHECK (passengers IS NULL OR passengers >= 0),
    currency         VARCHAR(3)   NOT NULL DEFAULT 'RWF',
    total_amount     NUMERIC(16,2) NOT NULL DEFAULT 0 CHECK (total_amount >= 0),
    status           VARCHAR(25)  NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT','REQUESTED','CONFIRMED','READY_FOR_DEPLOYMENT','DEPLOYED','READY_FOR_BILLING','COMPLETED','CANCELLED')),
    source           VARCHAR(20)  NOT NULL DEFAULT 'INTERNAL' CHECK (source IN ('INTERNAL','WEBSITE','PHONE','EMAIL','API')),
    cancellation_reason VARCHAR(255),
    confirmed_at     TIMESTAMPTZ,
    deployed_at      TIMESTAMPTZ,
    completed_at     TIMESTAMPTZ,
    cancelled_at     TIMESTAMPTZ,
    notes            TEXT,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by       VARCHAR(150),
    updated_by       VARCHAR(150),
    version          BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_bookings_period CHECK (start_date <= end_date)
);
CREATE INDEX ix_bookings_customer ON bookings (customer_id);
CREATE INDEX ix_bookings_status ON bookings (status);
CREATE INDEX ix_bookings_dates ON bookings (start_date, end_date);

-- Requested vehicles per booking (category x quantity x pricing type)
CREATE TABLE booking_lines (
    id                BIGSERIAL PRIMARY KEY,
    booking_id        BIGINT       NOT NULL REFERENCES bookings (id) ON DELETE CASCADE,
    category_id       BIGINT       NOT NULL REFERENCES vehicle_categories (id),
    preferred_model   VARCHAR(100),
    quantity          INT          NOT NULL CHECK (quantity > 0),
    pricing_type      VARCHAR(20)  NOT NULL CHECK (pricing_type IN ('FULL_DAY','HALF_DAY','PER_TRIP','MONTHLY','PER_KM')),
    start_date        DATE         NOT NULL,
    end_date          DATE         NOT NULL,
    unit_price        NUMERIC(14,2) NOT NULL CHECK (unit_price >= 0),
    line_total        NUMERIC(16,2) NOT NULL CHECK (line_total >= 0),
    notes             VARCHAR(255),
    CONSTRAINT ck_booking_lines_period CHECK (start_date <= end_date)
);
CREATE INDEX ix_booking_lines_booking ON booking_lines (booking_id);

-- One slot per requested vehicle; the dispatcher assigns a vehicle + driver to each slot.
CREATE TABLE booking_slots (
    id               BIGSERIAL PRIMARY KEY,
    booking_id       BIGINT       NOT NULL REFERENCES bookings (id) ON DELETE CASCADE,
    booking_line_id  BIGINT       NOT NULL REFERENCES booking_lines (id) ON DELETE CASCADE,
    slot_number      INT          NOT NULL,
    category_id      BIGINT       NOT NULL REFERENCES vehicle_categories (id),
    start_date       DATE         NOT NULL,
    end_date         DATE         NOT NULL,
    shift            VARCHAR(10)  NOT NULL DEFAULT 'DAY' CHECK (shift IN ('DAY','NIGHT','FULL')),
    vehicle_id       BIGINT REFERENCES vehicles (id),
    driver_id        BIGINT REFERENCES drivers (id),
    status           VARCHAR(20)  NOT NULL DEFAULT 'UNASSIGNED'
        CHECK (status IN ('UNASSIGNED','ASSIGNED','DEPLOYED','RETURNED','CANCELLED')),
    assigned_at      TIMESTAMPTZ,
    assigned_by_user_id BIGINT REFERENCES users (id) ON DELETE SET NULL,
    departed_at      TIMESTAMPTZ,
    returned_at      TIMESTAMPTZ,
    odometer_out     BIGINT CHECK (odometer_out IS NULL OR odometer_out >= 0),
    odometer_in      BIGINT CHECK (odometer_in IS NULL OR odometer_in >= 0),
    trip_id          BIGINT,
    notes            VARCHAR(255),
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by       VARCHAR(150),
    updated_by       VARCHAR(150),
    version          BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_booking_slots_period CHECK (start_date <= end_date),
    CONSTRAINT ux_booking_slot_number UNIQUE (booking_id, slot_number)
);
CREATE INDEX ix_booking_slots_vehicle_period ON booking_slots (vehicle_id, start_date, end_date) WHERE vehicle_id IS NOT NULL;
CREATE INDEX ix_booking_slots_driver_period ON booking_slots (driver_id, start_date, end_date) WHERE driver_id IS NOT NULL;
CREATE INDEX ix_booking_slots_status ON booking_slots (status);

CREATE TABLE booking_extra_charges (
    id          BIGSERIAL PRIMARY KEY,
    booking_id  BIGINT        NOT NULL REFERENCES bookings (id) ON DELETE CASCADE,
    description VARCHAR(255)  NOT NULL,
    amount      NUMERIC(14,2) NOT NULL CHECK (amount >= 0),
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    created_by  VARCHAR(150)
);

-- Local purchase orders raised by clients (LPO-2026-0188)
CREATE TABLE purchase_orders (
    id             BIGSERIAL PRIMARY KEY,
    lpo_number     VARCHAR(30)  NOT NULL UNIQUE,
    customer_id    BIGINT       NOT NULL REFERENCES customers (id),
    commitment_id  BIGINT REFERENCES commitments (id) ON DELETE SET NULL,
    booking_id     BIGINT REFERENCES bookings (id) ON DELETE SET NULL,
    issued_date    DATE         NOT NULL,
    expiry_date    DATE,
    received_date  DATE,
    value          NUMERIC(16,2) NOT NULL CHECK (value >= 0),
    currency       VARCHAR(3)   NOT NULL DEFAULT 'RWF',
    status         VARCHAR(20)  NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN','PART_INVOICED','INVOICED','EXPIRED','CLOSED','CANCELLED')),
    attachment_id  BIGINT REFERENCES attachments (id) ON DELETE SET NULL,
    notes          VARCHAR(500),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by     VARCHAR(150),
    updated_by     VARCHAR(150),
    version        BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_purchase_orders_dates CHECK (expiry_date IS NULL OR issued_date <= expiry_date)
);
CREATE INDEX ix_purchase_orders_customer ON purchase_orders (customer_id);
CREATE INDEX ix_purchase_orders_booking ON purchase_orders (booking_id);

CREATE TABLE trips (
    id                 BIGSERIAL PRIMARY KEY,
    trip_number        VARCHAR(20)  NOT NULL UNIQUE,
    vehicle_id         BIGINT       NOT NULL REFERENCES vehicles (id),
    driver_id          BIGINT       NOT NULL REFERENCES drivers (id),
    customer_id        BIGINT REFERENCES customers (id) ON DELETE SET NULL,
    booking_id         BIGINT REFERENCES bookings (id) ON DELETE SET NULL,
    booking_slot_id    BIGINT REFERENCES booking_slots (id) ON DELETE SET NULL,
    origin             VARCHAR(255) NOT NULL,
    destination        VARCHAR(255) NOT NULL,
    route_description  VARCHAR(500),
    purpose            VARCHAR(255),
    passenger_details  VARCHAR(500),
    passengers         INT CHECK (passengers IS NULL OR passengers >= 0),
    scheduled_start_at TIMESTAMPTZ  NOT NULL,
    scheduled_end_at   TIMESTAMPTZ,
    started_at         TIMESTAMPTZ,
    ended_at           TIMESTAMPTZ,
    start_odometer_km  BIGINT CHECK (start_odometer_km IS NULL OR start_odometer_km >= 0),
    end_odometer_km    BIGINT CHECK (end_odometer_km IS NULL OR end_odometer_km >= 0),
    distance_km        NUMERIC(10,1) CHECK (distance_km IS NULL OR distance_km >= 0),
    duration_minutes   INT CHECK (duration_minutes IS NULL OR duration_minutes >= 0),
    fuel_used_litres   NUMERIC(10,2) CHECK (fuel_used_litres IS NULL OR fuel_used_litres >= 0),
    max_speed_kph      NUMERIC(6,1) CHECK (max_speed_kph IS NULL OR max_speed_kph >= 0),
    status             VARCHAR(20)  NOT NULL DEFAULT 'PLANNED'
        CHECK (status IN ('PLANNED','DISPATCHED','IN_PROGRESS','COMPLETED','CANCELLED')),
    cancellation_reason VARCHAR(255),
    notes              TEXT,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by         VARCHAR(150),
    updated_by         VARCHAR(150),
    version            BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_trips_schedule CHECK (scheduled_end_at IS NULL OR scheduled_start_at <= scheduled_end_at),
    CONSTRAINT ck_trips_actual CHECK (started_at IS NULL OR ended_at IS NULL OR started_at <= ended_at),
    CONSTRAINT ck_trips_odometer CHECK (start_odometer_km IS NULL OR end_odometer_km IS NULL OR start_odometer_km <= end_odometer_km)
);
CREATE INDEX ix_trips_vehicle ON trips (vehicle_id, scheduled_start_at DESC);
CREATE INDEX ix_trips_driver ON trips (driver_id, scheduled_start_at DESC);
CREATE INDEX ix_trips_status ON trips (status);
CREATE INDEX ix_trips_booking ON trips (booking_id);
CREATE INDEX ix_trips_started ON trips (started_at);

ALTER TABLE booking_slots ADD CONSTRAINT fk_booking_slots_trip FOREIGN KEY (trip_id) REFERENCES trips (id) ON DELETE SET NULL;

-- Per-vehicle deployment voucher (LIMOZ/000628/2026): the printable dispatch & billing document for one slot.
CREATE TABLE deployment_vouchers (
    id                   BIGSERIAL PRIMARY KEY,
    voucher_number       VARCHAR(30)  NOT NULL UNIQUE,
    booking_slot_id      BIGINT       NOT NULL UNIQUE REFERENCES booking_slots (id) ON DELETE CASCADE,
    booking_id           BIGINT       NOT NULL REFERENCES bookings (id) ON DELETE CASCADE,
    customer_id          BIGINT       NOT NULL REFERENCES customers (id),
    purchase_order_id    BIGINT REFERENCES purchase_orders (id) ON DELETE SET NULL,
    vehicle_id           BIGINT       NOT NULL REFERENCES vehicles (id),
    driver_id            BIGINT       NOT NULL REFERENCES drivers (id),
    account_manager_user_id BIGINT REFERENCES users (id) ON DELETE SET NULL,
    voucher_date         DATE         NOT NULL,
    destination          VARCHAR(255),
    client_tel           VARCHAR(30),
    owner_name           VARCHAR(150),
    owner_driver_name    VARCHAR(120),
    start_km             BIGINT CHECK (start_km IS NULL OR start_km >= 0),
    end_km               BIGINT CHECK (end_km IS NULL OR end_km >= 0),
    planned_days         INT          NOT NULL DEFAULT 1 CHECK (planned_days > 0),
    effective_days       NUMERIC(8,3) NOT NULL DEFAULT 0 CHECK (effective_days >= 0),
    day_rate             NUMERIC(14,2) NOT NULL DEFAULT 0 CHECK (day_rate >= 0),
    institution_amount   NUMERIC(16,2) NOT NULL DEFAULT 0 CHECK (institution_amount >= 0),
    owner_amount         NUMERIC(16,2) NOT NULL DEFAULT 0 CHECK (owner_amount >= 0),
    fuel_amount          NUMERIC(16,2) NOT NULL DEFAULT 0 CHECK (fuel_amount >= 0),
    net_amount           NUMERIC(16,2) NOT NULL DEFAULT 0,
    mission_due_amount   NUMERIC(16,2) NOT NULL DEFAULT 0 CHECK (mission_due_amount >= 0),
    po_amount            NUMERIC(16,2) CHECK (po_amount IS NULL OR po_amount >= 0),
    status               VARCHAR(20)  NOT NULL DEFAULT 'ONGOING' CHECK (status IN ('ONGOING','RETURNED','NOT_RETURNED','INVOICED','CANCELLED')),
    comment              VARCHAR(500),
    observation          TEXT,
    returned_at          TIMESTAMPTZ,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by           VARCHAR(150),
    updated_by           VARCHAR(150),
    version              BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_vouchers_km CHECK (start_km IS NULL OR end_km IS NULL OR end_km >= start_km)
);
CREATE INDEX ix_vouchers_booking ON deployment_vouchers (booking_id);
CREATE INDEX ix_vouchers_vehicle ON deployment_vouchers (vehicle_id, voucher_date DESC);
CREATE INDEX ix_vouchers_status ON deployment_vouchers (status);
