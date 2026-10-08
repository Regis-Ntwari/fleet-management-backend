# LIMOZ Rwanda - Fleet Operations Management System (Backend)

Production-grade REST backend for LIMOZ Rwanda Ltd's fleet operations: vehicles, drivers, assignments, bookings and
dispatch, trips, fuel, maintenance & garage, spare parts, incidents & fines, clients/commitments/LPOs, invoicing,
payments, expenses, documents & certificates, telematics & daily movement analysis, notifications, alert centre,
dashboard, reports, imports, audit log and role-based access control.

| | |
|---|---|
| Runtime | Java 21, Spring Boot 4.1 (Spring Web MVC, Data JPA/Hibernate 7, Security 7 with JWT), PostgreSQL 17, Flyway |
| Quality | JUnit 5, Mockito, MockMvc, embedded PostgreSQL integration tests (no Docker needed), JaCoCo, GitHub Actions |
| Docs | OpenAPI 3 / Swagger UI, `docs/01-reference-application-analysis.md`, `docs/02-requirements-and-architecture.md`, `docs/dev-conventions.md` |
| Ops | Docker (layered, non-root image), docker-compose, actuator health/metrics/prometheus |

## 1. Prerequisites

* **Java 21** (JDK) - `java -version`
* **PostgreSQL 14+** (17 recommended) - or Docker to run it (`make db-up`)
* **Maven** - not required, the Maven wrapper `./mvnw` downloads Maven 3.9 automatically
* **Docker + Docker Compose v2** - optional, for the containerised setup
* Node.js 20+ - only for the React frontend (separate project)

## 2. Quick start

### Option A - everything in Docker

```bash
cp .env.example .env            # edit DB_PASSWORD, JWT_SECRET (openssl rand -base64 48), ADMIN_PASSWORD
docker compose up -d --build    # PostgreSQL + backend (dev profile, realistic seed data)
open http://localhost:8080/swagger-ui.html
```

### Option B - local JVM, PostgreSQL in Docker

```bash
make db-up                      # or: docker compose up -d postgres
./mvnw spring-boot:run          # dev profile by default: migrations + bootstrap admin + seed data
```

### Option C - local JVM, your own PostgreSQL

```sql
CREATE USER limoz WITH PASSWORD 'limoz';
CREATE DATABASE limoz_fleet OWNER limoz;
```

```bash
export DB_HOST=localhost DB_PORT=5432 DB_NAME=limoz_fleet DB_USERNAME=limoz DB_PASSWORD=limoz
./mvnw spring-boot:run
```

The API is served at `http://localhost:8080`, Swagger UI at `/swagger-ui.html`, health at `/actuator/health`.

## 3. Configuration

All configuration is read from environment variables (see `.env.example`); nothing needs to be edited in source.

| Variable | Default | Purpose |
|----------|---------|---------|
| `SPRING_PROFILES_ACTIVE` | `dev` | `dev` (seed data, Swagger, permissive defaults) or `prod` |
| `DB_HOST` / `DB_PORT` / `DB_NAME` / `DB_USERNAME` / `DB_PASSWORD` | localhost / 5432 / limoz_fleet / limoz / limoz | PostgreSQL connection |
| `JWT_SECRET` | dev-only value in `dev` profile, **required** otherwise | HMAC key, >= 32 bytes |
| `JWT_ACCESS_TTL` / `JWT_REFRESH_TTL` | `PT15M` / `P14D` | token lifetimes (ISO-8601 durations) |
| `ADMIN_EMAIL` / `ADMIN_PASSWORD` | `admin@limoz.rw` / `Admin@12345` (dev only) | bootstrap SUPER_ADMIN created when the user table is empty |
| `SEED_DATA` | `true` in dev, `false` otherwise | load the realistic development dataset on first start |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173,http://localhost:3000` | frontend origins |
| `FLEET_TIMEZONE` | `Africa/Kigali` | operational timezone (display + business days); storage is UTC |
| `STORAGE_PROVIDER` / `STORAGE_LOCAL_PATH` | `local` / `./data/uploads` | attachment storage (`FileStorage` abstraction; S3 adapter can be added) |
| `TELEMATICS_PROVIDER` | `none` | GPS provider adapter (`TelematicsProvider` interface; see `docs/telematics-integration.md`) |
| `SWAGGER_ENABLED` | `false` in prod | expose Swagger UI in production |

Operational thresholds (night-driving hours, excessive driving hours, high daily distance, fuel variance tolerance,
document warning days, maintenance intervals, GPS offline minutes, VAT, lockout policy...) are **runtime settings**
stored in the database and editable by `SETTINGS_MANAGE` users via `PUT /api/v1/settings` - not source code.

## 4. Database & migrations

Schema changes are version-controlled with Flyway (`src/main/resources/db/migration`):

| Migration | Content |
|-----------|---------|
| `V1` | users, roles, permissions, refresh tokens, settings, audit log, attachments, reference sequences |
| `V2` | permission catalogue, 12 system roles with their permission matrix, default settings |
| `V3` | vehicle categories, vehicles, odometer journal, drivers, document types, vehicle/driver documents, assignments, telematics devices |
| `V4` | clients, commitments, bookings (lines, slots, extra charges), LPOs, trips, deployment vouchers |
| `V5` | fuel transactions, workshops, service types, maintenance records/tasks/parts/comments, spare parts, stock movements, schedules |
| `V6` | incidents & history, traffic fines, invoices, expenses, payments |
| `V7` | vehicle positions, daily movement summaries, notifications, alerts |

Migrations run automatically at start-up (`spring.jpa.hibernate.ddl-auto=validate` guarantees entities match the
schema). Never use `ddl-auto=create` in production.

## 5. Seed data & login accounts

With `SEED_DATA=true` (dev default) and an empty database the application loads a realistic dataset: vehicle
categories, ~25 vehicles, drivers, clients with commitments and LPOs, bookings/deployments, trips, fuel records,
maintenance jobs and schedules, spare parts, incidents, fines, invoices, payments, expenses, documents, GPS positions
and alerts - enough for every dashboard chart and report to be meaningful. All seed passwords are `Limoz@2026`:

| Email | Role |
|-------|------|
| `admin@limoz.rw` (password from `ADMIN_PASSWORD`, dev default `Admin@12345`) | SUPER_ADMIN |
| `it.admin@limoz.rw` | IT_ADMIN |
| `management@limoz.rw` | MANAGEMENT |
| `fleet.manager@limoz.rw` | FLEET_MANAGER |
| `fleet.officer@limoz.rw` | FLEET_OFFICER |
| `dispatcher@limoz.rw` | DISPATCHER |
| `workshop@limoz.rw` | WORKSHOP_MANAGER |
| `technician@limoz.rw` | TECHNICIAN |
| `finance@limoz.rw` | FINANCE |
| `compliance@limoz.rw` | COMPLIANCE_OFFICER |
| `driver@limoz.rw` | DRIVER (linked to a driver record) |
| `viewer@limoz.rw` | VIEWER |

## 6. API

* Swagger UI: `http://localhost:8080/swagger-ui.html` - OpenAPI JSON: `/v3/api-docs` (`make openapi` saves it to `docs/openapi.json`).
* Authenticate: `POST /api/v1/auth/login` `{"email":"admin@limoz.rw","password":"..."}` -> `accessToken`, `refreshToken`, `user`.
  Send `Authorization: Bearer <accessToken>`; refresh with `POST /api/v1/auth/refresh`; `POST /api/v1/auth/logout` revokes.
* Every list endpoint is paginated: `?page=0&size=20&sort=plateNumber,asc` and filtered server-side
  (`?q=RAD&status=AVAILABLE&categoryId=2`). Responses use the `PageResponse` envelope.
* Errors share one shape (`ApiError`): `{timestamp,status,error,message,path,code,errors[]}` -
  400 validation (per-field `errors`), 401 authentication, 403 permission, 404 missing, 409 duplicate/concurrency,
  422 business-rule violation with a stable `code` (e.g. `VEHICLE_NOT_AVAILABLE`, `ODOMETER_DECREASE`, `DRIVER_LICENSE_EXPIRED`).
* Authorization is enforced on the server with permission codes (`VEHICLE_READ`, `TRIP_MANAGE`, `DISPATCH_MANAGE`,
  `MAINTENANCE_APPROVE`, `FINANCE_MANAGE`, `REPORT_EXPORT`, `USER_MANAGE`...) attached to roles; `GET /api/v1/auth/me`
  returns the caller's permissions so the frontend can hide what the user may not do.

The full endpoint map is in `docs/02-requirements-and-architecture.md` (section 8).

## 7. Testing

```bash
./mvnw test                      # unit + integration tests (~2 min)
./mvnw verify                    # + JaCoCo report in target/site/jacoco/index.html
./mvnw test -Dtest=VehicleIT     # one class
```

Integration tests (`*IT`) start an **embedded PostgreSQL** (Zonky) inside the test JVM - no Docker or external
database required - and run every Flyway migration, so schema, constraints and JSONB columns are exercised for real.
They cover login/refresh/logout/revocation, authorization per role, vehicle creation and duplicate-plate prevention,
odometer rules, assignment conflicts and licence validation, documents, imports, reports/exports, dispatch, trips,
fuel and maintenance calculations and lifecycles, invoices/payments, incidents, telematics ingestion, daily movement,
notifications and alerts.

## 8. Building & deployment

```bash
./mvnw -B clean package -DskipTests          # target/fleet-operations-backend.jar
java -jar target/fleet-operations-backend.jar --spring.profiles.active=prod   # reads env vars
docker build -t limoz/fleet-operations-backend:1.0.0 .
```

**docker-compose** (`docker-compose.yml`) runs PostgreSQL, the backend and - behind `--profile frontend` - the React
frontend once the `frontend/` project exists. Data lives in named volumes (`postgres-data`, `backend-uploads`).

**CI/CD** (`.github/workflows/ci.yml`): every push/PR builds and runs the full test suite with coverage; pushes to
`main`/`develop` and `v*` tags build a layered image and push it to GitHub Container Registry
(`ghcr.io/<org>/<repo>:<tag>`); tags trigger a manual-approval production deploy job (SSH + `docker compose pull && up`)
using repository secrets `DEPLOY_HOST`, `DEPLOY_USER`, `DEPLOY_SSH_KEY`, `DEPLOY_PATH`.

Production checklist: strong `JWT_SECRET`, real `DB_PASSWORD`, `ADMIN_PASSWORD` set once then rotated in-app,
`SEED_DATA=false`, `SWAGGER_ENABLED=false` (or protected), HTTPS termination in front (nginx/Traefik), backups of
`postgres-data`, object storage adapter for attachments if running more than one instance.

## 9. Project structure

```
src/main/java/com/limoz/fleet/
  config/        security, cache, OpenAPI, CORS, clock, JPA auditing, typed properties
  security/      JWT issue/validate, principal, permission & role constants, throttling
  common/        base entity, ApiError + global handler, PageResponse, reference numbers, events, specifications
  <module>/      one package per business module, each split into
      controller/  REST endpoints (@PreAuthorize on every method)
      domain/      entities, enums, state machines, calculators, domain events
      dto/         request / response / summary / filter records
      mapper/      entity -> DTO mappers
      repository/  Spring Data repositories, specifications, aggregate queries
      service/     business rules, audit, events, importers, report providers, scanners
      job/         scheduled jobs
  modules: auth, user, settings, audit, storage, vehicle (+timeline), driver, assignment, document, customer,
           booking, trip, fuel, maintenance (+inventory), incident, finance, telematics (+movement),
           notification (+alert), dashboard, reporting (+export), search, importer, seed
src/main/resources/db/migration/   Flyway migrations
src/test/java/                     unit tests (*Test) and integration tests (*IT), one package per module
docs/                              analysis, requirements & architecture, frontend guide, conventions
```

Contribution rules for new modules: `docs/dev-conventions.md`.
