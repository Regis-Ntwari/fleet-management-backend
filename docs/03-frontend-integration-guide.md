# Frontend integration guide (React + TypeScript + Vite)

How to consume this backend from the React application. Everything here is stable across modules.

## 1. Base URL, CORS and tooling

* Base URL: `http://localhost:8080` in development (`VITE_API_BASE_URL`). All business endpoints are under `/api/v1`.
* CORS: allowed origins come from `CORS_ALLOWED_ORIGINS` (default `http://localhost:5173,http://localhost:3000`).
  `Content-Disposition` is exposed so downloads can read the file name.
* OpenAPI: `GET /v3/api-docs` - generate the TypeScript client/types with
  `npx openapi-typescript http://localhost:8080/v3/api-docs -o src/types/api.d.ts` (or orval / openapi-generator).
  Swagger UI at `/swagger-ui.html` documents parameters, request bodies, responses and error codes.

## 2. Authentication flow

```ts
// POST /api/v1/auth/login
{ "email": "admin@limoz.rw", "password": "..." }
// 200
{ "accessToken": "<jwt>", "refreshToken": "<opaque>", "tokenType": "Bearer", "expiresIn": 900,
  "user": { "id": 1, "fullName": "System Administrator", "email": "...", "roles": ["SUPER_ADMIN"],
            "permissions": ["VEHICLE_READ", "VEHICLE_CREATE", ...], "mustChangePassword": false, "driverId": null } }
```

* Send `Authorization: Bearer <accessToken>` on every request (Axios interceptor).
* Access tokens live 15 minutes. On a `401` with `code: "UNAUTHORIZED"`, call `POST /api/v1/auth/refresh`
  `{ "refreshToken": "..." }`, store the **new pair** (tokens rotate; an old refresh token is rejected and a reuse
  revokes the whole session family), then retry the request once. On refresh failure, go to `/login`.
* `POST /api/v1/auth/logout` `{ "refreshToken": "..." }` revokes both tokens immediately.
* `GET /api/v1/auth/me` returns the profile; use `user.permissions` to show/hide navigation and buttons.
  The server enforces the same permissions, so hiding is only cosmetic.
* `mustChangePassword: true` -> route to a change-password screen (`POST /api/v1/auth/change-password`).
* Store tokens in memory (+ refresh token in `sessionStorage`/`localStorage` according to your security policy); the API
  uses no cookies, so CSRF is not a concern.

## 3. Lists: pagination, sorting, filtering, search

Every list endpoint accepts `page` (0-based), `size`, `sort=field,asc|desc` (repeatable) plus module-specific filters
(`q` full-text-ish search, `status` repeatable, ids, `from`/`to` dates as `YYYY-MM-DD`):

```
GET /api/v1/vehicles?q=RAD&status=AVAILABLE&status=ASSIGNED&categoryId=3&page=0&size=20&sort=plateNumber,asc
```

Response envelope (`PageResponse<T>`):

```json
{ "content": [...], "page": 0, "size": 20, "totalElements": 48, "totalPages": 3, "first": true, "last": false }
```

Use TanStack Query with the full parameter set as the query key (`['vehicles', params]`), `keepPreviousData` for paging,
and invalidate the list + `['dashboard']` after mutations. Dropdown data comes from `/summaries` endpoints
(`/api/v1/vehicles/summaries?status=AVAILABLE`, `/api/v1/drivers/summaries`, `/api/v1/customers/summaries`) - never from
the paginated list.

Global search box (⌘K): `GET /api/v1/search?q=RAD 408&limit=5` returns groups (`vehicles`, `drivers`, `clients`,
`bookings`, `trips`, `maintenance`, `incidents`...) with `linkPath` for navigation.

## 4. Errors

All errors share one body; map it once in the Axios response interceptor:

```json
{ "timestamp": "2026-06-04T08:00:00Z", "status": 422, "error": "Business Rule Violation",
  "message": "Vehicle RAD 408 C is IN_MAINTENANCE and cannot be assigned", "path": "/api/v1/assignments",
  "code": "VEHICLE_NOT_AVAILABLE", "errors": null }
```

| HTTP | `code` | Frontend handling |
|------|--------|-------------------|
| 400 | `VALIDATION_ERROR` (+ `errors[{field, rejectedValue, message}]`) | show field errors on the form |
| 400 | `BAD_REQUEST` | toast |
| 401 | `UNAUTHORIZED`, `BAD_CREDENTIALS`, `ACCOUNT_LOCKED`, `ACCOUNT_DISABLED` | refresh / login screen message |
| 403 | `FORBIDDEN` | "You do not have permission" state |
| 404 | `NOT_FOUND` | not-found state |
| 409 | `DUPLICATE`, `DATA_INTEGRITY`, `CONCURRENT_MODIFICATION` | inline message (e.g. plate exists) / reload prompt |
| 422 | business codes (`VEHICLE_NOT_AVAILABLE`, `DRIVER_LICENSE_EXPIRED`, `ODOMETER_DECREASE`, `INVALID_STATE_TRANSITION`, `INSUFFICIENT_STOCK`, `OVERPAYMENT`...) | show `message` next to the action |
| 429 | `RATE_LIMITED` | "too many attempts" |
| 500 | `INTERNAL_ERROR` | generic error state |

## 5. Enumerations and reference data

Statuses are **not** to be hard-coded in components. Load once and cache:

* `GET /api/v1/vehicle-categories`, `/document-types`, `/service-types`, `/workshops`, `/expense-categories`, `/roles`
* `GET /api/v1/settings/company` (company profile, currency, timezone), `GET /api/v1/settings` (thresholds, admins)
* Status vocabularies are documented in the OpenAPI schema enums (e.g. `VehicleStatus`, `BookingStatus`,
  `MaintenanceRecordStatus`); generate TS types from the spec and map them to badge colours:
  AVAILABLE / VALID / COMPLETED / PAID -> green; ASSIGNED / ON_TRIP / DEPLOYED / IN_PROGRESS -> blue;
  EXPIRING_SOON / DUE_SOON / PENDING / WAITING_FOR_PARTS -> amber; EXPIRED / OVERDUE / CRITICAL / OUT_OF_SERVICE -> red;
  IN_MAINTENANCE / IN_WORKSHOP -> orange; INACTIVE / CANCELLED / DRAFT -> grey.

## 6. Dates, times and money

* Timestamps are ISO-8601 UTC instants (`2026-06-04T06:30:00Z`); format in the browser with the company timezone
  (`Africa/Kigali`, from `/settings/company`), e.g. `Intl.DateTimeFormat('en-GB', { timeZone })`.
* Business dates are plain `YYYY-MM-DD` strings (booking dates, document expiry) - do not convert them through `Date`.
* Money fields are decimal numbers with the record's `currency` (`RWF` default, `USD` on some contracts). Format with
  `Intl.NumberFormat('en-RW', { style: 'currency', currency })`.

## 7. Files

* Upload: `POST /api/v1/attachments` (multipart `file`, `ownerType`, `ownerId`, `category`) -> `{ id, downloadUrl }`;
  then put the returned `id` into `attachmentId` fields (documents, LPO, receipts, incident photos).
* Download: `GET /api/v1/attachments/{id}/download` with the bearer header (fetch as blob, read `Content-Disposition`).
* Reports: `GET /api/v1/reports/{code}?format=json` for on-screen tables (`columns` + `rows` + `totals`), and
  `format=csv|xlsx|pdf` for downloads (requires `REPORT_EXPORT`). `GET /api/v1/reports` lists what the user may run.
* Imports: `POST /api/v1/imports/vehicles|drivers|fuel` (multipart) -> `ImportResult` with `errors[{row, reference, reason}]`
  to render a rejection table. `GET /api/v1/imports/templates` gives the expected columns.

## 8. Dashboard & alerts

`/api/v1/dashboard/summary` (KPI cards), `/fleet-status` (donut), `/utilization`, `/fuel-trend`, `/maintenance`,
`/trips-per-day`, `/distance-trend`, `/cost-by-vehicle`, `/availability-trend` (charts), `/alerts` (priority actions),
`/deployments-today`, `/recent-bookings` (panels). All accept `date`/`from`/`to`/`categoryId` where relevant and are
cached 60 s server-side - poll every 60 s or refetch on focus. Alerts carry `linkPath` to deep-link into the record.

`/api/v1/notifications?unreadOnly=true` + `/notifications/unread-count` feed the bell; `POST /notifications/{id}/read`.

## 9. Workflow endpoints (actions, not generic PUTs)

State changes are explicit POST actions so the UI can map one button to one call and the server validates the
transition: `/bookings/{id}/confirm|cancel|complete`, `/bookings/slots/{id}/assign|unassign|depart|return`,
`/trips/{id}/dispatch|start|complete|cancel`, `/maintenance/{id}/review|approve|start|wait-parts|resume|complete|release|cancel`,
`/incidents/{id}/investigate|resolve|close|reopen`, `/invoices/{id}/issue|cancel`, `/expenses/{id}/approve|reject|pay`,
`/traffic-fines/{id}/pay|dispute|waive`, `/alerts/{id}/acknowledge|resolve`, `/users/{id}/enable|disable|reset-password`.
Destructive actions (archive vehicle, cancel booking, close incident, disable user) should be confirmed in the UI;
the server keeps history (soft delete / status change) in every case.

## 10. Suggested frontend structure

```
src/api/client.ts            axios instance + auth interceptors + error mapping
src/api/<module>.ts          typed functions per endpoint (generated types from OpenAPI)
src/auth/                    AuthProvider, usePermission(), <RequirePermission code="VEHICLE_CREATE">
src/features/<module>/       queries.ts (TanStack Query hooks), components, pages
src/components/              DataTable (server paging), StatusBadge, KpiCard, FilterBar, ConfirmDialog, FileUpload
src/routes/                  React Router config matching docs/02 section 9
```
