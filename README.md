# lynk

A URL shortener service: submit a long URL, get back a short one, and every redirect is counted.

Built with Spring Boot 4.1, Java 25 and PostgreSQL. API only — there is no web interface.

## Features

- **Shorten** a long URL to a 7-character base-62 code
- **Custom aliases** for memorable links, with a uniqueness guarantee
- **Expiration**: any link can be given a time-to-live, and expired links stop redirecting
- **Click tracking** via an atomic counter, safe under concurrent redirects
- **RFC 9457 problem details** for every error response
- **Nightly cleanup** that deletes expired links
- **Containerised** with a multi-stage build and a Compose stack

## Requirements

| Tool   | Version                             |
|--------|-------------------------------------|
| JDK    | 25                                  |
| Docker | any recent version, with Compose v2 |

Everything else — Maven, the build toolchain, PostgreSQL — comes from the wrapper, the build
images, or the containers. There is nothing to install but a JDK and Docker.

## Quick start

```console
$ ./mvnw spring-boot:run
```

The first run takes a little longer while Maven resolves dependencies and the database image pulls.
Once it is up:

```console
$ curl -s -X POST http://localhost:8080/api/v1/url/shorten \
    -H 'Content-Type: application/json' \
    -d '{"url":"https://spring.io/guides","customAlias":"readme-demo"}'
$ curl -sI http://localhost:8080/readme-demo
```

`Ctrl-C` stops the app and the database it started.

## API

| Method | Path                       | Success | Notes                                     |
|--------|----------------------------|---------|-------------------------------------------|
| `POST` | `/api/v1/url/shorten`      | `201`   | `{url, customAlias?, hoursToExpire?}`     |
| `GET`  | `/{shortCode}`             | `302`   | redirects and increments the click count  |
| `GET`  | `/api/v1/url/stats/{code}` | `200`   | click stats, including for expired links  |
| `GET`  | `/actuator/health`         | `200`   | health, including the database connection |

### Shorten a URL

```console
$ curl -X POST http://localhost:8080/api/v1/url/shorten \
    -H 'Content-Type: application/json' \
    -d '{"url":"https://spring.io/guides","customAlias":"readme-demo","hoursToExpire":24}'
{
  "shortUrl":   "http://localhost:8080/readme-demo",
  "shortCode":  "readme-demo",
  "originalUrl":"https://spring.io/guides",
  "expiresAt":  "2026-09-29T05:09:39.484046Z"
}
```

`customAlias` and `hoursToExpire` are both optional. Omit `hoursToExpire` and the link never expires.

| Field           | Rules                                                                             |
|-----------------|-----------------------------------------------------------------------------------|
| `url`           | required, non-blank, absolute `http`/`https` with a host, at most 2048 characters |
| `customAlias`   | optional, 3–32 characters of `A-Za-z0-9_-`, not a reserved word, unique           |
| `hoursToExpire` | optional, 1–8760 (one year); omitted means the link never expires                 |

The scheme is lower-cased on the way in, so `HTTPS://…` and `https://…` store the same
destination.

The `201` response also carries a `Location` header holding the same value as `shortUrl`, so a
client can follow or store the new short link without parsing the body:

```console
$ curl -i -X POST http://localhost:8080/api/v1/url/shorten \
    -H 'Content-Type: application/json' \
    -d '{"url":"https://spring.io/guides"}'
HTTP/1.1 201
Location: http://localhost:8080/Ab3xY9z
```

### Follow a short link

```console
$ curl -i http://localhost:8080/readme-demo
HTTP/1.1 302
Location: https://spring.io/guides
Cache-Control: no-store
```

`Cache-Control: no-store` matters: without it, intermediaries and browsers cache the redirect and
the click count silently under-reports. `302` rather than `301` because both the destination and
the count can change, so the redirect must never be cached as permanent.

### Read the stats

```console
$ curl http://localhost:8080/api/v1/url/stats/readme-demo
{
  "originalUrl": "https://spring.io/guides",
  "shortUrl":    "http://localhost:8080/readme-demo",
  "createdAt":   "2026-09-28T05:09:39.484023Z",
  "expiresAt":   "2026-09-29T05:09:39.484046Z",
  "clickCount":  12
}
```

Stats keep working after a link expires, and until the nightly sweep deletes it. `expiresAt` is
`null` for a link that never expires.

### Errors

Every error is a problem document, and nothing else:

```console
$ curl http://localhost:8080/zzzzzzz
{
  "detail":   "No shortened URL exists for code 'zzzzzzz'.",
  "instance": "/zzzzzzz",
  "status":   404,
  "title":    "Short URL not found",
  "type":     "/problems/url-not-found"
}
```

| Status | `type`                        | Meaning                                 |
|--------|-------------------------------|-----------------------------------------|
| `400`  | `/problems/validation-failed` | bean validation failed; see `errors[]`  |
| `400`  | `/problems/invalid-url`       | URL or alias unusable                   |
| `400`  | `/problems/reserved-alias`    | alias would shadow an application route |
| `404`  | `/problems/url-not-found`     | no such short code                      |
| `409`  | `/problems/alias-conflict`    | custom alias already taken              |
| `410`  | `/problems/url-expired`       | link existed but has expired            |

`type` is the stable, machine-readable handle; `title` and `detail` are prose and may be reworded.
Branch on `type` and `status`, never on `detail`.

A validation failure adds a per-field `errors` array, since RFC 9457 has no standard field for it:

```json
{
  "type": "/problems/validation-failed",
  "title": "Validation failed",
  "status": 400,
  "instance": "/api/v1/url/shorten",
  "errors": [
    {
      "field": "url",
      "message": "url must not be blank",
      "rejectedValue": ""
    },
    {
      "field": "hoursToExpire",
      "message": "hoursToExpire must be at least 1",
      "rejectedValue": "0"
    }
  ]
}
```

`410 Gone` is used rather than `404` for expired links, so a caller holding a stale link can tell
"this will never work again" from "you never had this link".

## Running locally

Requires Docker. No local PostgreSQL installation is needed — the `spring-boot-docker-compose`
module finds `compose.yaml`, starts the database, wires the datasource, and stops the database
again on shutdown.

```console
./mvnw spring-boot:run
```

The first run takes a little longer while Maven resolves dependencies and the database image pulls.
The dev database publishes `5432` on a random host port so it cannot clash with a PostgreSQL you
already run; its data lives in the `lynk-dev-db` volume and survives restarts. Discard it with
`docker compose -f compose.yaml down -v`.

Tests are split into two suites, selected by JUnit tag rather than by class name:

```console
./mvnw test      # unit tests only; no Docker needed, runs in about a second
./mvnw verify    # unit tests plus integration tests; needs Docker
```

Surefire is configured with `<groups>unit</groups>` and Failsafe with `<groups>integration</groups>`,
so a class joins a suite by carrying `@Tag("unit")` or extending the `@Tag("integration")` base
class. The `*Test` / `*IT` naming matches that split and keeps Failsafe's default includes in step,
but the tag is what actually decides. The integration suite runs against a real PostgreSQL via
Testcontainers, exercising the actual Flyway migrations rather than an in-memory stand-in. Splitting
them means a failing unit test stops the build before the container-backed suite spins up, and
`./mvnw test` stays usable on a machine with no Docker running.

Every build also runs Spotless (`palantirJavaFormat`) in the `validate` phase, so a formatting
violation fails `./mvnw test` before a single test executes. Fix it with `./mvnw spotless:apply`.

The integration tests are independent of `compose.yaml`: Docker Compose support is disabled during
test runs, so the two do not fight over a database.

## Running with Docker

```console
export POSTGRES_PASSWORD=change-me
export APP_BASE_URL=https://sho.rt          # what callers will type
docker compose -f compose.prod.yaml up --build
```

`APP_BASE_URL` is not cosmetic: it is what gets baked into the `shortUrl` in every response, so it
must be the address users actually reach the service at.

`POSTGRES_PASSWORD` has no default and Compose refuses to start without it.

The app container waits for the database to report healthy, and has its own healthcheck against
`/actuator/health`. Both services are `restart: unless-stopped`, and the image runs as an
unprivileged user.

To stop and discard the data:

```console
docker compose -f compose.prod.yaml down -v
```

## Configuration

| Variable                        | Default                 | Purpose                           |
|---------------------------------|-------------------------|-----------------------------------|
| `APP_BASE_URL`                  | `http://localhost:8080` | base for returned `shortUrl`      |
| `DB_URL`                        | — (required in prod)    | JDBC URL                          |
| `DB_USERNAME` / `DB_PASSWORD`   | — (required in prod)    | credentials                       |
| `DB_POOL_SIZE`                  | `10`                    | Hikari maximum pool size          |
| `POSTGRES_DB` / `POSTGRES_USER` | `lynk`                  | database name and user            |
| `POSTGRES_PASSWORD`             | — (required)            | database password                 |
| `APP_PORT`                      | `8080`                  | host port mapped to the container |

Non-secret settings live under `lynk.*` in `application.yml`:

| Key                   | Default                        | Purpose                                |
|-----------------------|--------------------------------|----------------------------------------|
| `code.length`         | `7`                            | base-62 characters in a generated code |
| `code.max-attempts`   | `5`                            | retries on a generated-code collision  |
| `code.max-url-length` | `2048`                         | upper bound on the accepted long URL   |
| `cleanup.cron`        | `0 0 1 * * ?`                  | when the nightly expiry sweep runs     |
| `reserved-words`      | `error, api, actuator, health` | aliases that may never be claimed      |

The `prod` profile additionally sets `spring.docker.compose.enabled=false` and
`server.shutdown=graceful`, so the container never shells out to Docker and in-flight redirects
finish on shutdown.

## Project layout

```
src/main/java/com/lynk/
  config/       @ConfigurationProperties record, @EnableScheduling
  controller/   the two HTTP entry points: /api/v1/url/* and /{shortCode}
  domain/       the UrlMapping entity
  dto/          request and response records — the entity never leaves the service
  error/        exceptions plus the single @RestControllerAdvice that maps them to problem details
  repository/   Spring Data JPA interface, including the atomic click increment
  service/      shortening, resolution, validation, code generation, expiry sweep
src/main/resources/db/migration/   Flyway; the schema's only source of truth
src/test/java/com/lynk/            *Test = @Tag("unit"), *IT = @Tag("integration") via Testcontainers
```

## Design notes

**Short codes are random.** Encoding the row id in base-62 would leak sequential ids, so codes come
from `SecureRandom` instead, with a retry on the unique constraint to cover the rare collision. With
62^7 possible codes the retry almost never fires.

**Click counting is a single-column atomic update.** Redirects are the hot path, so they run
`click_count = click_count + 1` rather than loading the entity, mutating it and saving. That is
correct under concurrent redirects, which the read-modify-write version is not. The repository
method sets `flushAutomatically` and `clearAutomatically` so the mapping read earlier in the same
transaction cannot be flushed back over the counter.

**Flyway owns the schema.** `ddl-auto` is `validate`, so Hibernate verifies the entities match
`V1__create_url_mapping.sql` and never issues DDL. Change the migration, not the database.

**Timestamps are `timestamptz` mapped to `Instant`.** `LocalDateTime` carries no zone, and pairing
it with `timestamptz` quietly makes expiry depend on the JVM's default zone. `Instant` makes the
comparison unambiguous.

**The redirect route is constrained.** `/{shortCode:[A-Za-z0-9_-]{3,32}}` rather than
`/{shortCode}`. Without the bound, a bare single-segment path like `/error` would be treated as a
missing short URL instead of a missing page. Note that `error` falls *inside* the pattern, which is
why it is in `reserved-words` and cannot be claimed as a custom alias.

**Virtual threads are enabled** (`spring.threads.virtual.enabled`). A redirect is one indexed read
plus one atomic update, which is exactly the I/O-bound profile virtual threads suit.

**Expiry is enforced on the read path, not by the sweeper.** `resolveAndCountClick` re-checks
`expires_at` in the same transaction as the increment, so a link stops redirecting the moment it
expires whether or not the nightly job ever fires. The job only reclaims storage.

**The URL is parsed and normalised before it is stored.** `UrlValidator` accepts only absolute
`http`/`https` URIs with a host and rebuilds the string from a lower-cased scheme. That is what
guarantees the `URI.create(...)` on the redirect path cannot throw after the mapping is committed —
an unparseable `Location` would otherwise turn a stored link into a 500.

**Aliases are checked twice, and the database decides.** The `existsByShortCode` pre-check gives an
ordinary claim a clean 409, but two racing requests can both pass it; the unique constraint on
`short_code` is what actually makes the guarantee, and the `DataIntegrityViolationException` is
translated into the same 409.

**Reserved words are compared case-insensitively**, because the redirect pattern is
case-sensitive-per-character and `/ERROR` would shadow the error page just as `/error` does.

## Known limitations

These are deliberate scope decisions, not oversights:

- **No authentication.** Anyone who knows a short code can read its click count. Adding accounts
  would mean owning the link namespace per user.
- **No rate limiting** on `POST /api/v1/url/shorten`, so the endpoint is open to abuse by anyone
  who can reach it.
- **Click totals only.** No per-request analytics, so there is no referrer, user-agent or
  timestamped click history.
- **The cleanup schedule is fixed at boot.** `cleanup.cron` is read when the job is registered, so
  changing it needs a restart.
- **One row per link.** Shortening the same URL twice creates two independent links with separate
  click counts; there is no deduplication or URL canonicalisation beyond scheme casing.
- **A single instance owns the cleanup schedule.** Nothing stops several app instances from running
  the same `@Scheduled` sweep, which is harmless here (the delete is idempotent) but is not a
  leader-elected job.

## Development

```console
./mvnw spotless:apply   # format before committing; the build checks this in validate
./mvnw test             # unit tests, no Docker
./mvnw verify           # unit + integration tests, needs Docker
./mvnw spring-boot:run  # run against compose.yaml
```

### Committing

`spotless:apply` is the mandatory first step: Spotless runs in the `validate` phase, so a formatting
violation fails `./mvnw test` and blocks the commit anyway. Run it before staging rather than
amending afterwards.

```console
./mvnw spotless:apply
./mvnw test             # ~1s, unit tests only; the full verify needs Docker
git add -A
git commit -m "Describe the change and, where it is not obvious, why"
git push
```

Commit messages describe the *reason* for a change, not just its mechanics — the design notes above
already explain how the system works, so a message that only repeats the diff is worth nothing six
months from now.

### Branching

**Protected Branches**: `main` and `develop` cannot be modified directly. Changes must go through pull requests.

- `main` is the default branch and is expected to stay green. Branch off it for anything non-trivial:
- `develop` is used for feature integration and testing

**Development Workflow**:
1. Create a feature branch from `develop`: `git switch -c feature/<user>/<feature-name>`
2. Make your changes and push them to your feature branch
3. Create a pull request targeting `develop` for features or `main` for hotfixes
4. Wait for CI tests to pass and get required approvals
5. Merge through the pull request (using squash or rebase)
6. Delete the feature branch: `git branch -d feature/<user>/<feature-name>`

**Merges back to `main`** use `--no-ff`, so the branch is visible in the history and the reason for the change survives in its commits.

```console
git switch -c fix/alias-race-on-409
# ...work, then: ./mvnw spotless:apply && ./mvnw test
git switch main && git merge --no-ff fix/alias-race-on-409
git branch -d fix/alias-race-on-409
```

Merges back to `main` use `--no-ff`, so the branch is visible in the history and the reason for the
change survives in its commits. CI runs `./mvnw -B verify` on Java 25, which includes the
Testcontainers integration tests — the same suite `./mvnw verify` runs locally, given a running
Docker.

Two repository details worth knowing before your first commit: `.gitattributes` normalises line
endings, so `mvnw.cmd` is stored with `LF` and checked out as `CRLF` on Windows (a `LF will be
replaced by CRLF` warning from `git add` is expected, not a problem), and `target/` is ignored.

## License

MIT. See [LICENSE](LICENSE).
