-- =====================================================================
-- V6: incidents & accidents, traffic fines, invoices, payments, expenses
-- =====================================================================

CREATE TABLE incidents (
    id                   BIGSERIAL PRIMARY KEY,
    incident_number      VARCHAR(20)  NOT NULL UNIQUE,
    vehicle_id           BIGINT       NOT NULL REFERENCES vehicles (id),
    driver_id            BIGINT REFERENCES drivers (id) ON DELETE SET NULL,
    trip_id              BIGINT REFERENCES trips (id) ON DELETE SET NULL,
    occurred_at          TIMESTAMPTZ  NOT NULL,
    location             VARCHAR(255),
    latitude             NUMERIC(9,6),
    longitude            NUMERIC(9,6),
    incident_type        VARCHAR(25)  NOT NULL
        CHECK (incident_type IN ('ACCIDENT','BREAKDOWN','THEFT','VANDALISM','FUEL_ANOMALY','TRAFFIC_VIOLATION','DAMAGE','OTHER')),
    severity             VARCHAR(10)  NOT NULL DEFAULT 'MINOR' CHECK (severity IN ('MINOR','MODERATE','MAJOR','CRITICAL')),
    description          TEXT         NOT NULL,
    third_party_involved BOOLEAN      NOT NULL DEFAULT FALSE,
    injuries             BOOLEAN      NOT NULL DEFAULT FALSE,
    police_report_number VARCHAR(60),
    fuel_loss_litres     NUMERIC(10,2) CHECK (fuel_loss_litres IS NULL OR fuel_loss_litres >= 0),
    estimated_cost       NUMERIC(14,2) CHECK (estimated_cost IS NULL OR estimated_cost >= 0),
    investigation_notes  TEXT,
    corrective_action    TEXT,
    status               VARCHAR(20)  NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN','UNDER_INVESTIGATION','RESOLVED','CLOSED')),
    reported_by_user_id  BIGINT REFERENCES users (id) ON DELETE SET NULL,
    resolved_at          TIMESTAMPTZ,
    closed_at            TIMESTAMPTZ,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by           VARCHAR(150),
    updated_by           VARCHAR(150),
    version              BIGINT       NOT NULL DEFAULT 0
);
CREATE INDEX ix_incidents_vehicle ON incidents (vehicle_id, occurred_at DESC);
CREATE INDEX ix_incidents_driver ON incidents (driver_id);
CREATE INDEX ix_incidents_status ON incidents (status);
CREATE INDEX ix_incidents_occurred ON incidents (occurred_at);
ALTER TABLE maintenance_records ADD CONSTRAINT fk_maintenance_incident FOREIGN KEY (incident_id) REFERENCES incidents (id) ON DELETE SET NULL;

-- Full investigation history of an incident
CREATE TABLE incident_updates (
    id           BIGSERIAL PRIMARY KEY,
    incident_id  BIGINT       NOT NULL REFERENCES incidents (id) ON DELETE CASCADE,
    update_type  VARCHAR(20)  NOT NULL CHECK (update_type IN ('NOTE','STATUS_CHANGE','ATTACHMENT','ASSIGNMENT')),
    from_status  VARCHAR(20),
    to_status    VARCHAR(20),
    note         TEXT,
    user_id      BIGINT REFERENCES users (id) ON DELETE SET NULL,
    author_name  VARCHAR(150),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX ix_incident_updates_incident ON incident_updates (incident_id, created_at);

CREATE TABLE traffic_fines (
    id              BIGSERIAL PRIMARY KEY,
    fine_number     VARCHAR(20)   NOT NULL UNIQUE,
    ticket_reference VARCHAR(60),
    vehicle_id      BIGINT        NOT NULL REFERENCES vehicles (id),
    driver_id       BIGINT REFERENCES drivers (id) ON DELETE SET NULL,
    trip_id         BIGINT REFERENCES trips (id) ON DELETE SET NULL,
    issued_at       TIMESTAMPTZ   NOT NULL,
    location        VARCHAR(255),
    offence         VARCHAR(255)  NOT NULL,
    amount          NUMERIC(14,2) NOT NULL CHECK (amount >= 0),
    currency        VARCHAR(3)    NOT NULL DEFAULT 'RWF',
    due_date        DATE,
    status          VARCHAR(10)   NOT NULL DEFAULT 'UNPAID' CHECK (status IN ('UNPAID','PAID','DISPUTED','WAIVED')),
    paid_at         TIMESTAMPTZ,
    payment_id      BIGINT,
    charged_to_driver BOOLEAN     NOT NULL DEFAULT FALSE,
    attachment_id   BIGINT REFERENCES attachments (id) ON DELETE SET NULL,
    notes           VARCHAR(500),
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    created_by      VARCHAR(150),
    updated_by      VARCHAR(150),
    version         BIGINT        NOT NULL DEFAULT 0
);
CREATE INDEX ix_fines_vehicle ON traffic_fines (vehicle_id, issued_at DESC);
CREATE INDEX ix_fines_driver ON traffic_fines (driver_id);
CREATE INDEX ix_fines_status ON traffic_fines (status);

CREATE TABLE invoices (
    id                BIGSERIAL PRIMARY KEY,
    invoice_number    VARCHAR(20)   NOT NULL UNIQUE,
    customer_id       BIGINT        NOT NULL REFERENCES customers (id),
    booking_id        BIGINT REFERENCES bookings (id) ON DELETE SET NULL,
    purchase_order_id BIGINT REFERENCES purchase_orders (id) ON DELETE SET NULL,
    issue_date        DATE          NOT NULL,
    due_date          DATE          NOT NULL,
    payment_terms     VARCHAR(20)   NOT NULL DEFAULT 'NET_30'
        CHECK (payment_terms IN ('DUE_ON_RECEIPT','NET_7','NET_14','NET_30','NET_45','NET_60')),
    currency          VARCHAR(3)    NOT NULL DEFAULT 'RWF',
    subtotal          NUMERIC(16,2) NOT NULL DEFAULT 0 CHECK (subtotal >= 0),
    discount_percent  NUMERIC(5,2)  NOT NULL DEFAULT 0 CHECK (discount_percent >= 0 AND discount_percent <= 100),
    discount_amount   NUMERIC(16,2) NOT NULL DEFAULT 0 CHECK (discount_amount >= 0),
    tax_amount        NUMERIC(16,2) NOT NULL DEFAULT 0 CHECK (tax_amount >= 0),
    total_amount      NUMERIC(16,2) NOT NULL DEFAULT 0 CHECK (total_amount >= 0),
    amount_paid       NUMERIC(16,2) NOT NULL DEFAULT 0 CHECK (amount_paid >= 0),
    status            VARCHAR(15)   NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT','ISSUED','PARTIALLY_PAID','PAID','OVERDUE','CANCELLED')),
    sent_at           TIMESTAMPTZ,
    paid_at           TIMESTAMPTZ,
    notes             TEXT,
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    created_by        VARCHAR(150),
    updated_by        VARCHAR(150),
    version           BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT ck_invoices_dates CHECK (issue_date <= due_date)
);
CREATE INDEX ix_invoices_customer ON invoices (customer_id);
CREATE INDEX ix_invoices_status ON invoices (status);
CREATE INDEX ix_invoices_booking ON invoices (booking_id);
CREATE INDEX ix_invoices_due ON invoices (due_date);

CREATE TABLE invoice_lines (
    id              BIGSERIAL PRIMARY KEY,
    invoice_id      BIGINT        NOT NULL REFERENCES invoices (id) ON DELETE CASCADE,
    booking_line_id BIGINT REFERENCES booking_lines (id) ON DELETE SET NULL,
    deployment_voucher_id BIGINT REFERENCES deployment_vouchers (id) ON DELETE SET NULL,
    description     VARCHAR(255)  NOT NULL,
    quantity        NUMERIC(10,3) NOT NULL CHECK (quantity > 0),
    unit_price      NUMERIC(14,2) NOT NULL CHECK (unit_price >= 0),
    tax_percent     NUMERIC(5,2)  NOT NULL DEFAULT 0 CHECK (tax_percent >= 0),
    line_total      NUMERIC(16,2) NOT NULL CHECK (line_total >= 0),
    sort_order      INT           NOT NULL DEFAULT 0
);
CREATE INDEX ix_invoice_lines_invoice ON invoice_lines (invoice_id);

CREATE TABLE expense_categories (
    id         BIGSERIAL PRIMARY KEY,
    code       VARCHAR(40)  NOT NULL UNIQUE,
    name       VARCHAR(80)  NOT NULL,
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    sort_order INT          NOT NULL DEFAULT 0
);

CREATE TABLE expenses (
    id                   BIGSERIAL PRIMARY KEY,
    expense_number       VARCHAR(20)   NOT NULL UNIQUE,
    category_id          BIGINT        NOT NULL REFERENCES expense_categories (id),
    description          VARCHAR(255)  NOT NULL,
    amount               NUMERIC(14,2) NOT NULL CHECK (amount >= 0),
    currency             VARCHAR(3)    NOT NULL DEFAULT 'RWF',
    incurred_on          DATE          NOT NULL,
    vehicle_id           BIGINT REFERENCES vehicles (id) ON DELETE SET NULL,
    driver_id            BIGINT REFERENCES drivers (id) ON DELETE SET NULL,
    trip_id              BIGINT REFERENCES trips (id) ON DELETE SET NULL,
    booking_id           BIGINT REFERENCES bookings (id) ON DELETE SET NULL,
    submitted_by_user_id BIGINT REFERENCES users (id) ON DELETE SET NULL,
    submitted_by_name    VARCHAR(150),
    status               VARCHAR(10)   NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','APPROVED','REJECTED','PAID')),
    approved_by_user_id  BIGINT REFERENCES users (id) ON DELETE SET NULL,
    approved_at          TIMESTAMPTZ,
    rejection_reason     VARCHAR(255),
    receipt_attachment_id BIGINT REFERENCES attachments (id) ON DELETE SET NULL,
    notes                VARCHAR(500),
    created_at           TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    created_by           VARCHAR(150),
    updated_by           VARCHAR(150),
    version              BIGINT        NOT NULL DEFAULT 0
);
CREATE INDEX ix_expenses_vehicle ON expenses (vehicle_id);
CREATE INDEX ix_expenses_status ON expenses (status);
CREATE INDEX ix_expenses_date ON expenses (incurred_on);

-- Payment ledger: money received from clients (IN) and paid to vendors/garages (OUT)
CREATE TABLE payments (
    id                    BIGSERIAL PRIMARY KEY,
    payment_number        VARCHAR(20)   NOT NULL UNIQUE,
    direction             VARCHAR(3)    NOT NULL CHECK (direction IN ('IN','OUT')),
    counterparty_name     VARCHAR(150)  NOT NULL,
    customer_id           BIGINT REFERENCES customers (id) ON DELETE SET NULL,
    invoice_id            BIGINT REFERENCES invoices (id) ON DELETE SET NULL,
    maintenance_record_id BIGINT REFERENCES maintenance_records (id) ON DELETE SET NULL,
    expense_id            BIGINT REFERENCES expenses (id) ON DELETE SET NULL,
    traffic_fine_id       BIGINT REFERENCES traffic_fines (id) ON DELETE SET NULL,
    method                VARCHAR(20)   NOT NULL CHECK (method IN ('CASH','BANK_TRANSFER','MOBILE_MONEY','CHEQUE','CARD','OTHER')),
    amount                NUMERIC(16,2) NOT NULL CHECK (amount > 0),
    currency              VARCHAR(3)    NOT NULL DEFAULT 'RWF',
    paid_at               TIMESTAMPTZ   NOT NULL,
    external_reference    VARCHAR(80),
    receipt_attachment_id BIGINT REFERENCES attachments (id) ON DELETE SET NULL,
    recorded_by_user_id   BIGINT REFERENCES users (id) ON DELETE SET NULL,
    notes                 VARCHAR(500),
    reversed              BOOLEAN       NOT NULL DEFAULT FALSE,
    reversal_reason       VARCHAR(255),
    created_at            TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    created_by            VARCHAR(150),
    updated_by            VARCHAR(150),
    version               BIGINT        NOT NULL DEFAULT 0
);
CREATE INDEX ix_payments_invoice ON payments (invoice_id);
CREATE INDEX ix_payments_customer ON payments (customer_id);
CREATE INDEX ix_payments_paid_at ON payments (paid_at DESC);
ALTER TABLE traffic_fines ADD CONSTRAINT fk_fines_payment FOREIGN KEY (payment_id) REFERENCES payments (id) ON DELETE SET NULL;

INSERT INTO expense_categories (code, name, sort_order) VALUES
 ('TOLLS_PARKING','Tolls & parking',1),
 ('CLEANING','Cleaning',2),
 ('OFFICE','Office',3),
 ('DRIVER_ALLOWANCE','Driver allowance',4),
 ('PERMITS','Permits',5),
 ('REPAIRS','Repairs',6),
 ('TYRES','Tyres',7),
 ('INSURANCE','Insurance',8),
 ('OTHER','Other',99);
