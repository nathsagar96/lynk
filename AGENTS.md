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
- `url_mapping.owner` is **nullable on purpose**: `V2` could not set `NOT NULL` without
  deleting links that already existed. Those rows match no owner, so their stats read as a 404.
  Don't tighten the column without backfilling or purging them first.
- The paged listing sorts by `created_at` then `id`. The `id` tiebreaker is load-bearing — two links
  registered in one transaction share a timestamp — so don't drop it.

## HTTP/routing gotchas

- Redirect route is `/{shortCode:[A-Za-z0-9_-]{3,32}}`. `error` matches the pattern, so
  it (and the other `reserved-words`) must stay claimed in `application.yml`, or a
  custom alias can shadow the error page. Reserved-word comparison is case-insensitive
  on purpose.
- Redirects must stay `302` with `Cache-Control: no-store` — `301` or a missing header
  silently corrupts click counts.
- All errors go through the single `@RestControllerAdvice` as RFC 9457 problem
  documents, **except** `401`s, which the security filter chain answers before MVC reaches the
  advice — see `ProblemAuthenticationEntryPoint`; branch clients on `type`/`status`, never `detail`.
- Bean validation on a **query parameter** raises `HandlerMethodValidationException`, which
  `ApiExceptionHandler` reshapes into the same `/problems/validation-failed` document a bad body gets,
  so clients have one type for validation. Spring 7 named the hook
  `handleHandlerMethodValidationException` — not `handleHandlerMethodValidation`.
- `RedirectController` is `@Hidden` — the redirect route is for browsers following a
  link, not an operation a client calls, and in the OpenAPI doc it would read as a
  catch-all `/{shortCode}`. Don't document it or remove the annotation.
- Examples belong on the DTO fields as `@Schema(example = ...)`, never as `@ExampleObject`
  on an operation or a response. Problem-document responses carry **no** example at all;
  name the possible `type` values in the `@ApiResponse` description instead.

## Security / auth gotchas

- Boot 4 starter names are `spring-boot-starter-security-oauth2-resource-server` (runtime)
  and `spring-boot-starter-security-oauth2-resource-server-test` (test; brings
  `spring-security-test` for the `jwt()` post-processor). The pre-4 names are not used.
- The service is a Keycloak **resource server** — it only validates tokens. `KEYCLOAK_ISSUER_URI`
  (default: the realm `compose.yaml` imports) is read at **startup**, when the JWK set is
  discovered, so Keycloak must be reachable before the app boots. That is why `compose.yaml` starts
  Keycloak alongside the database.
- `401`s are produced by the filter chain, upstream of MVC, so they never reach
  `ApiExceptionHandler`. `ProblemAuthenticationEntryPoint` writes the equivalent problem document;
  keep it in step if the error shape changes.
- Rules in `SecurityConfig`: `/api/**` needs a token, everything else is `permitAll()`. The redirect
  and health must stay public, and keeping unmatched paths permitted is what keeps a bad path a
  `404` rather than a `403`.
- Links are owned by the token's `sub`. `UrlController.ownerOf` is where that claim is read and
  where a token without one is rejected — a link registered under no subject could never be read
  back, not even by its creator. Stats are scoped by owner, and a link you do not own is a **404,
  not 403**, because a 403 would confirm the code exists — the paged listing is scoped the same way
  but returns an empty page rather than a 404, since it is not keyed by a code.
  `resolveAndCountClick` stays owner-agnostic
  (the redirect is public) and short codes stay globally unique: a public redirect cannot resolve a
  per-user namespace.
- Every integration suite except `KeycloakAuthenticationIT` gets a mocked `JwtDecoder` from
  `AbstractIntegrationTestBase`, so they boot without a live Keycloak; that one class wires a real
  container's issuer instead and must not extend the base.

## Boot 4 / Jackson

- The JSON mapper is **Jackson 3** (`tools.jackson.databind`). There is no
  `com.fasterxml.jackson.databind.ObjectMapper` bean — inject `tools.jackson.databind.ObjectMapper`
  (the auto-configured bean is a `tools.jackson.databind.json.JsonMapper`). Jackson 2 is present
  only transitively, for a couple of test libraries.

## Running

- Dev: `./mvnw spring-boot:run` — spring-boot-docker-compose starts PostgreSQL (random host
  port) and Keycloak (host `8081`, realm `lynk`), and tears them down on exit. Keycloak must be up
  before the app, since the issuer is discovered at startup — which is why it is in `compose.yaml`.
  `POSTGRES_PASSWORD` has no default in `compose.prod.yaml`; the prod stack refuses to start without
  it, `APP_BASE_URL`, and `KEYCLOAK_ISSUER_URI`, and `APP_BASE_URL` is baked into every returned
  `shortUrl`.
- `prod` profile disables docker-compose, enables graceful shutdown and sets
  `springdoc.api-docs.enabled=false` (which also takes the Swagger UI down, since both come from the
  same autoconfiguration); don't enable compose support there. It has no default
  `KEYCLOAK_ISSUER_URI`, so prod points at a real realm rather than the dev container.
