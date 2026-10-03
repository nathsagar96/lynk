# AGENTS.md

Spring Boot 4.1 / Java 25 / PostgreSQL URL shortener. Single Maven module, no web UI.
`README.md` is detailed and accurate — consult it; this file only lists what is easy to
get wrong.

## Verify changes (run in this order)

```console
./mvnw spotless:apply   # REQUIRED before committing; Spotless runs in `validate`
                        # and a format violation fails ./mvnw test
./mvnw test             # unit tests only, no Docker needed (~1s)
./mvnw verify           # adds integration tests + JaCoCo coverage gate; requires Docker
```

CI runs `./mvnw -B verify` on Java 25 (Temurin). `verify` also fails if bundle coverage drops below
line 80% / branch 70% (JaCoCo `check`); the merged report lands in `target/site/jacoco/`.
`-Djacoco.skip=true` bypasses it locally.

## Test layout quirks

- Suites are selected by **JUnit tag**, not class name: `@Tag("unit")` for surefire (`<groups>unit</groups>`),
  `@Tag("integration")` (via extending
  `AbstractIntegrationTestBase`) for failsafe. A new test class without the right tag
  silently runs in the wrong suite or not at all.
- Integration tests use Testcontainers against real PostgreSQL and run the real Flyway
  migrations. Docker Compose support is disabled during tests on purpose — do not
  "fix" test DB wiring through `compose.yaml`.
- To run one integration class: `./mvnw verify -Dit.test=FooIT`. To run one unit class:
  `./mvnw test -Dtest=FooTest`.

## Schema / persistence rules

- **Flyway owns the schema**; Hibernate is `ddl-auto=validate` and never issues DDL.
  Changing an entity means adding a migration in `src/main/resources/db/migration/`,
  not editing the entity alone.
- Click increments are an atomic repository update (`flushAutomatically`/
  `clearAutomatically` set) — do not "simplify" to load-mutate-save; that breaks under
  concurrent redirects.
- Timestamps are `Instant` → `timestamptz`. Don't introduce `LocalDateTime`.

## HTTP/routing gotchas

- Redirect route is `/{shortCode:[A-Za-z0-9_-]{3,32}}`. `error` matches the pattern, so
  it (and the other `reserved-words`) must stay claimed in `application.yml`, or a
  custom alias can shadow the error page. Reserved-word comparison is case-insensitive
  on purpose.
- Redirects must stay `302` with `Cache-Control: no-store` — `301` or a missing header
  silently corrupts click counts.
- All errors go through the single `@RestControllerAdvice` as RFC 9457 problem
  documents; branch clients on `type`/`status`, never `detail`.
- `RedirectController` is `@Hidden` — the redirect route is for browsers following a
  link, not an operation a client calls, and in the OpenAPI doc it would read as a
  catch-all `/{shortCode}`. Don't document it or remove the annotation.
- Examples belong on the DTO fields as `@Schema(example = ...)`, never as `@ExampleObject`
  on an operation or a response. Problem-document responses carry **no** example at all;
  name the possible `type` values in the `@ApiResponse` description instead.

## Running

- Dev: `./mvnw spring-boot:run` — spring-boot-docker-compose starts PostgreSQL, maps it
  to a random host port, and tears it down on exit. `POSTGRES_PASSWORD` has no default
  in `compose.prod.yaml`; the prod stack refuses to start without it and `APP_BASE_URL`,
  and `APP_BASE_URL` is baked into every returned `shortUrl`.
- `prod` profile disables docker-compose, enables graceful shutdown and sets
  `springdoc.api-docs.enabled=false` (which also takes the Swagger UI down, since both come from the
  same autoconfiguration); don't enable compose support there.
