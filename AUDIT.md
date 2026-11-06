# JavaBank audit

Scope reviewed before implementation: Maven build, application classes, controllers, services, repositories, entities and DTOs, exception handling, configuration, Flyway migrations, tests, Docker files, `.gitignore`, and README.

## Critical

- **Transfers are not atomic and are race-prone** (`TransactionService`, `User`, `Transaction`): balance reads/updates and transaction insertion have no transaction boundary or locking/version column. Concurrent requests can overspend or lose updates; a failure after one repository save can leave inconsistent balances.
- **Money uses binary floating point** (`User.wallet`, `Transaction.transferredValue`, transaction DTOs/services): `Double` can introduce rounding errors in balances and transfers.
- **No authentication or ownership checks**: every caller can register/list users and submit transfers with arbitrary payer IDs. Anyone can spend from another user's account; there are no passwords, roles, JWTs, or transaction-history access controls.
- **Client-controlled registration balance** (`UserRegistrationRequestDto`, `UserService`): callers can create arbitrary funds by choosing `wallet`.
- **External notification is inside the transfer path without transactional safeguards** (`TransactionService`, `NotificationService`): an external failure raises an untyped exception after balance updates; persistence may already have occurred and the caller receives an ambiguous failure. External authorization is also a mandatory hard-coded network dependency.
- **Database configuration contains a committed plaintext password** (`src/main/resources/application.yml`) and uses `ddl-auto: create`, which can drop/recreate schema on startup.
- **Flyway schema does not match the JPA model**: migration V2 requires both `cpf` and `cnpj` for every user, while application registration sets only one; it uses a PostgreSQL enum while JPA enum mapping defaults to a string column; the transaction migration omits the transfer amount required by the entity and has no status; JPA `create` masks these mismatches. Migration V1 creates a database enum type, coupling the schema to PostgreSQL.

## High

- **Insufficient transfer validation**: no request-body binding annotations or `@Valid` on endpoints; no positive/minimum amount, distinct payer/payee, or sufficient-balance validation independent of the remote service; merchant restrictions are hidden in remote authorization rather than enforced as a local business rule.
- **External calls are hard-coded, unbounded, and weakly parsed** (`AuthorizationService`, `NotificationService`, `AppConfig`): `RestTemplate` has no timeouts; endpoints cannot be configured; raw `Map` response parsing can fail on null/shape changes; exceptions are uncaught; authorization serializes the whole payer JPA entity (including personal data and persistence details), while notification payloads include email addresses.
- **User/document validation is incorrect/incomplete** (`UserRegistrationRequestDto`): both `@CPF` and `@CNPJ` are applied to the same field, making valid values for either type fail the other validator; `@Length` is applied to a `Double`; wallet null, negative, and precision are not meaningfully validated.
- **Persistence constraints and entity mappings are inadequate**: relationships are not explicitly required/lazy; no database checks/indexes for business rules; no optimistic/pessimistic locking; Lombok `@Data` generates mutable entity equality/string methods that can recurse through relationships and leak PII; `UserType` enum constructor ignores its argument.
- **REST contract is incomplete**: endpoints omit `@RequestBody`, return no `Location` header, use incorrect error statuses (missing users/unauthorized transfers map to 400), use no pagination, and expose list-all users without authorization.
- **Error responses are inconsistent and leak implementation behavior** (`GlobalExceptionHandler`): business errors return plain strings, one validation response is not consistently constructed as RFC 7807, unexpected exceptions have no normalized response, and no security error handlers exist.
- **Tests do not exercise important behavior**: transfer test runs with both Spring and Mockito extensions but does not stub repository results; its test body is guarded by unstubbed mock lookups, so transfer assertions are skipped. The user test depends on generated ID `1` and database state. No meaningful controller, security, migration, transaction-rule, or concurrency tests exist.
- **Runtime defaults are not production-safe** (`application.yml`, test config): default application config points to one fixed container hostname and hard-coded credentials; `show-sql` is enabled; tests use schema creation rather than validating Flyway migrations; no dev/prod/test configuration strategy or externalized environment settings is documented.
- **Container deployment is fragile/insecure**: Dockerfile expects a prebuilt jar and has no build stage or non-root user; Compose uses a floating `postgres:latest`, hard-coded password, no health check/readiness condition, no persistent volume, and no `.env.example`.
- **No health endpoint or operational logging policy**: no Actuator dependency or health check; authorization prints to stdout; error paths lack structured, safe logging.

## Medium

- **Build/dependency modernization needed**: Spring Boot is 3.2.4 with Java 17 rather than Java 21 and a current stable Boot 3.x release; springdoc 2.3.0 is old; no Spring Security, Actuator, JWT support, PostgreSQL test strategy, or JaCoCo. Review dependency usage during migration and avoid redundant/unused dependencies.
- **Layering and code consistency**: controllers/services use field injection; services combine mapping and business logic; DTOs are nested under `entities`; responses are manually duplicated; generic `RuntimeException` is used for invalid document and remote failures.
- **Transaction history is not exposed**: a transaction table exists but there is no query for an authenticated user's sent/received history, pagination, status field, or stable ordering.
- **API documentation is incomplete**: the README only provides a short purpose/stack list and Swagger URL; no auth instructions, configuration, curl examples, Docker/test steps, or request collection exists. OpenAPI bearer auth is not configured.
- **`.gitignore` omits common secrets/env files and some generated artifacts**; local IntelliJ metadata exists but is already ignored and is not tracked.

## Low

- **Naming and documentation polish**: several names contain spelling errors (`transacion`, `methodArgumenProblemDetail`), comments are stale/boilerplate, endpoint descriptions and README links refer to legacy APIs.
- **Configuration/deployment polish**: Compose declares a legacy top-level `version`; the default image tags and artifact naming are not pinned/configurable; the current README claims Swagger is available without documenting a reproducible startup procedure.

## Audit decisions and caveats

- New account registration will initialize balances at zero to prevent client-side creation of funds; this deliberate business-rule change will be documented in `CHANGES.md`.
- Preserve the existing rule that only COMMON users can initiate transfers and the minimum transfer value of 1, while adding local validation and ownership authorization.
- Keep existing Flyway migrations as history and add a forward migration for the corrected schema.
- The initial environment check found Java 27 but no `mvn` executable; build and Docker verification must be attempted again after implementation, and any unavailable verification must be reported accurately.
