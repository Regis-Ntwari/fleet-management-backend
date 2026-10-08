-- =====================================================================
-- V1: platform core - users, RBAC, sessions, settings, audit, files, sequences
-- All timestamps are stored as TIMESTAMPTZ (UTC). Business dates use DATE.
-- =====================================================================

CREATE TABLE permissions (
    id          BIGSERIAL PRIMARY KEY,
    code        VARCHAR(60)  NOT NULL UNIQUE,
    module      VARCHAR(60)  NOT NULL,
    description VARCHAR(255)
);

CREATE TABLE roles (
    id          BIGSERIAL PRIMARY KEY,
    code        VARCHAR(50)  NOT NULL UNIQUE,
    name        VARCHAR(100) NOT NULL,
    description VARCHAR(255),
    system_role BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by  VARCHAR(150),
    updated_by  VARCHAR(150),
    version     BIGINT       NOT NULL DEFAULT 0
);

CREATE TABLE role_permissions (
    role_id       BIGINT NOT NULL REFERENCES roles (id) ON DELETE CASCADE,
    permission_id BIGINT NOT NULL REFERENCES permissions (id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE users (
    id                    BIGSERIAL PRIMARY KEY,
    first_name            VARCHAR(80)  NOT NULL,
    last_name             VARCHAR(80)  NOT NULL,
    email                 VARCHAR(150) NOT NULL,
    phone                 VARCHAR(30),
    username              VARCHAR(60),
    password_hash         VARCHAR(100) NOT NULL,
    active                BOOLEAN      NOT NULL DEFAULT TRUE,
    must_change_password  BOOLEAN      NOT NULL DEFAULT FALSE,
    last_login_at         TIMESTAMPTZ,
    failed_login_attempts INT          NOT NULL DEFAULT 0,
    locked_until          TIMESTAMPTZ,
    token_version         INT          NOT NULL DEFAULT 0,
    driver_id             BIGINT,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by            VARCHAR(150),
    updated_by            VARCHAR(150),
    version               BIGINT       NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX ux_users_email ON users (LOWER(email));
CREATE UNIQUE INDEX ux_users_username ON users (LOWER(username)) WHERE username IS NOT NULL;
CREATE INDEX ix_users_active ON users (active);

CREATE TABLE user_roles (
    user_id BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role_id BIGINT NOT NULL REFERENCES roles (id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE refresh_tokens (
    id               BIGSERIAL PRIMARY KEY,
    user_id          BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash       VARCHAR(64) NOT NULL UNIQUE,
    expires_at       TIMESTAMPTZ NOT NULL,
    revoked_at       TIMESTAMPTZ,
    replaced_by_hash VARCHAR(64),
    created_at       TIMESTAMPTZ NOT NULL,
    ip_address       VARCHAR(45),
    user_agent       VARCHAR(255)
);
CREATE INDEX ix_refresh_tokens_user ON refresh_tokens (user_id);
CREATE INDEX ix_refresh_tokens_expires ON refresh_tokens (expires_at);

CREATE TABLE system_settings (
    id            BIGSERIAL PRIMARY KEY,
    setting_key   VARCHAR(100) NOT NULL UNIQUE,
    setting_value TEXT,
    value_type    VARCHAR(20)  NOT NULL CHECK (value_type IN ('STRING','INTEGER','DECIMAL','BOOLEAN','TIME','JSON')),
    category      VARCHAR(50)  NOT NULL,
    description   VARCHAR(255),
    editable      BOOLEAN      NOT NULL DEFAULT TRUE,
    updated_by    VARCHAR(150),
    updated_at    TIMESTAMPTZ
);

CREATE TABLE audit_logs (
    id               BIGSERIAL PRIMARY KEY,
    user_id          BIGINT,
    username         VARCHAR(150),
    action           VARCHAR(30)  NOT NULL,
    entity_type      VARCHAR(60)  NOT NULL,
    entity_id        BIGINT,
    entity_reference VARCHAR(120),
    description      VARCHAR(500),
    previous_value   JSONB,
    new_value        JSONB,
    ip_address       VARCHAR(45),
    user_agent       VARCHAR(255),
    occurred_at      TIMESTAMPTZ  NOT NULL
);
CREATE INDEX ix_audit_entity ON audit_logs (entity_type, entity_id);
CREATE INDEX ix_audit_occurred ON audit_logs (occurred_at DESC);
CREATE INDEX ix_audit_user ON audit_logs (user_id);

CREATE TABLE attachments (
    id               BIGSERIAL PRIMARY KEY,
    file_name        VARCHAR(255) NOT NULL,
    content_type     VARCHAR(120) NOT NULL,
    size_bytes       BIGINT       NOT NULL CHECK (size_bytes >= 0),
    storage_provider VARCHAR(30)  NOT NULL,
    storage_key      VARCHAR(500) NOT NULL,
    owner_type       VARCHAR(60),
    owner_id         BIGINT,
    category         VARCHAR(60),
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by       VARCHAR(150),
    updated_by       VARCHAR(150),
    version          BIGINT       NOT NULL DEFAULT 0
);
CREATE INDEX ix_attachments_owner ON attachments (owner_type, owner_id);

CREATE TABLE reference_sequences (
    sequence_key VARCHAR(60) PRIMARY KEY,
    next_value   BIGINT NOT NULL CHECK (next_value > 0)
);
