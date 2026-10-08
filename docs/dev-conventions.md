# Backend development conventions

These rules keep every module consistent. Read them before adding a module; the `vehicle`, `driver`,
`assignment` and `document` packages are the reference implementations.

## Stack
Java 21, Spring Boot 4.1 (Spring Framework 7, Spring Security 7, Spring Data 2026.0, Hibernate 7.4,
**Jackson 3** - package `tools.jackson.*`, annotations still `com.fasterxml.jackson.annotation.*`),
Flyway, PostgreSQL, Caffeine cache, springdoc-openapi 3, Lombok, JUnit 5 + Mockito + MockMvc,
Zonky embedded PostgreSQL for integration tests (no Docker needed).

## Package layout
Every module is a package under `com.limoz.fleet` split by layer:
```
com.limoz.fleet.<module>/
  controller/   @RestController classes - HTTP only, @PreAuthorize on every method, no business logic
  domain/       JPA entities, enums (values MUST match the CHECK constraints in db/migration/V*.sql), pure
                calculators/state machines, domain events, module setting-key constants
  dto/          Java records: <X>Request (bean validation), <X>Response, <X>Summary, <X>Filter
  mapper/       @Component with hand-written toResponse()/toSummary() (no MapStruct)
  repository/   JpaRepository + JpaSpecificationExecutor, <X>Specifications, aggregate/native query helpers;
                @EntityGraph for list/detail loads (no N+1)
  service/      @Service @Transactional classes - all business rules, audit calls, events; also event
                listeners, importers/exporters, report providers, search/timeline sources, alert scanners
  job/          @Scheduled jobs (cron from fleet.jobs.* / fleet.telematics.*, zone fleet.timezone)
```
Sub-modules (`maintenance.inventory`, `telematics.movement`, `notification.alert`, `vehicle.timeline`,
`reporting.export`) follow the same layer split. Platform packages `common`, `config` and `security` keep their
own structure. Tests live in `src/test/java/com/limoz/fleet/<module>/` (module root package) and import the
layered classes.

The database schema is fixed by the Flyway migrations `V1`-`V7` (+ `V8x` module settings). **Do not add or alter
tables in a new migration unless a column is genuinely missing**; map entities to the existing columns exactly
(`spring.jpa.hibernate.ddl-auto=validate` fails the start-up otherwise).

## Entities
* `@Entity @Table(name = "...") @Getter @Setter @NoArgsConstructor`, extend `BaseEntity`.
* Enums: `@Enumerated(EnumType.STRING)`, column length as in SQL.
* Associations: `@ManyToOne(fetch = FetchType.LAZY)`; collections only when the aggregate owns them
  (e.g. booking lines) with `orphanRemoval = true`.
* Money: `BigDecimal` with `precision/scale` matching the column; distances `long`/`BigDecimal(10,1)` as in SQL.
* Timestamps: `Instant` (TIMESTAMPTZ). Business dates: `LocalDate`. Never `LocalDateTime`.
* Soft delete: `archived` flag where the table has one; never physically delete operational history.
* Cross-module references: use the entity (`Vehicle`, `Driver`, `Customer`, `User` id as `Long`) - load through the
  owning module's service (`vehicleService.loadActive(id)`, `driverService.loadActive(id)`, `customerService.loadActive(id)`).

## Services
* Constructor injection via Lombok `@RequiredArgsConstructor`.
* Class-level `@Transactional`; `@Transactional(readOnly = true)` on queries.
* Validation beyond bean validation throws:
  * `ResourceNotFoundException` -> 404
  * `DuplicateResourceException` -> 409
  * `BusinessRuleException(code, message)` -> 422 (use a stable UPPER_SNAKE code such as `VEHICLE_NOT_AVAILABLE`)
  * `InvalidStateTransitionException(entity, from, to)` -> 422 for illegal status moves
* Status lifecycles: implement an explicit `canTransitionTo(...)`/switch and reject anything else.
* Reference numbers: `referenceNumberService.next(ReferenceType.TRIP)` etc. (must be called inside a transaction).
* Odometer: never set `vehicle.setOdometerKm` directly - call
  `odometerService.record(vehicle, km, OdometerSource.TRIP, "Trip", tripId)`; it rejects decreasing readings.
* Vehicle/driver status changes driven by workflows go through `vehicleService.transition(vehicle, VehicleStatus.X, reason)`
  and `driverService.transition(driver, DriverStatus.X, reason)`; use `vehicleService.restingStatus(vehicle)` /
  `driverService.restingStatus(driver)` when a trip or job ends.
* Dispatch compliance: `documentService.missingOrExpiredRequiredDocuments(vehicleId, date)` (empty list = OK) and
  `driver.isLicenseValidOn(date)`; honour settings `dispatch.require_valid_documents` / `dispatch.require_valid_license`.
* Runtime thresholds: `settingsService.getInt/getDecimal/getBoolean/getTime(SettingKeys.X)` - never hard-code them.
* Time: inject `Clock clock` (zone Africa/Kigali) and `ZoneId operationalZone`; use `Instant.now(clock)` / `LocalDate.now(clock)`.
* Audit: every create/update/status change/delete calls
  `auditService.record(AuditAction.X, "EntityType", id, reference, beforeDto, afterDto, "description")`.
* Notifications: publish `events.publishEvent(OperationalEvent.of("INCIDENT_CREATED", Severity.CRITICAL, title, message,
  "Incident", id, number, "/incidents/" + id, Roles.FLEET_MANAGER, Roles.MANAGEMENT))` for anything management should hear about.
  Do not call the notification module directly.
* Caches: annotate dashboard-affecting mutations with `@CacheEvict(cacheNames = CacheConfig.DASHBOARD, allEntries = true)`;
  reference data lists with `@Cacheable(cacheNames = CacheConfig.REFERENCE_DATA, key = "'...'")` + evict on change.

## Controllers
* `@PreAuthorize("hasAuthority('XXX')")` on every endpoint using codes from `security.Permissions` (no new codes without
  updating V2 and `Permissions.java` - ask first).
* Lists: `PageResponse<T>` + `@ParameterObject @PageableDefault(size = 20, sort = "...") Pageable` + a `<X>Filter` record
  bound with `@ParameterObject`. Backend filtering via `common.util.Specifications` helpers.
* `@ResponseStatus(HttpStatus.CREATED)` for POST creates, `NO_CONTENT` for void.
* OpenAPI: `@Tag` on the class, `@Operation(summary = ...)` on non-obvious methods.
* Never return entities; always DTO records.

## Tests
* Integration: extend `support.AbstractIntegrationTest` (`@SpringBootTest` + MockMvc + embedded PostgreSQL, `adminToken`
  ready, `tokenFor(Roles.X)` for authorization checks, `support.TestData` for vehicles/drivers). Name `*IT.java`.
* Unit: plain JUnit 5 + Mockito for calculations and state machines. Name `*Test.java`.
* Cover: happy path, each business rule (422 code), duplicate (409), authorization (403), pagination.
* Run: `./mvnw test` (all) or `./mvnw test -Dtest=TripIT`.

## Style
4-space indent, `final` where obvious, no wildcard imports except `org.springframework.web.bind.annotation.*`,
meaningful names, Javadoc on non-trivial rules, no TODOs in committed code.
