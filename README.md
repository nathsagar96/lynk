# lynk

A URL shortener service: submit a long URL, get back a short one, and every redirect is counted.

Built with Spring Boot 4.1, Java 25 and PostgreSQL. API only — there is no web interface.

## Features

- **Shorten** a long URL to a 7-character base-62 code
- **Bearer-token security** backed by Keycloak, so writing and reading stats needs a token
- **Per-link ownership**: a link's stats are visible only to the subject that registered it
- **Custom aliases** for memorable links, with a uniqueness guarantee
- **Expiration**: any link can be given a time-to-live, and expired links stop redirecting
- **Click tracking** via an atomic counter, safe under concurrent redirects
- **RFC 9457 problem details** for every error response
- **OpenAPI 3.1** contract with Swagger UI, generated from the controllers and DTOs
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

The first run takes a little longer while Maven resolves dependencies and pulls the database and
Keycloak images. `spring-boot:run` starts both from `compose.yaml`; Keycloak listens on `8081` and
imports the `lynk` realm.

The write and stats endpoints need a bearer token, so ask Keycloak for one first. The realm ships a
`lynk` / `lynk` user and a `lynk-cli` client for exactly this:

```console
$ TOKEN=$(curl -s -X POST http://localhost:8081/realms/lynk/protocol/openid-connect/token \
    -d grant_type=password -d client_id=lynk-cli -d username=lynk -d password=lynk \
    | jq -r .access_token)
$ curl -s -X POST http://localhost:8080/api/v1/url/shorten \
    -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
    -d '{"url":"https://spring.io/guides","customAlias":"readme-demo"}'
$ curl -sI http://localhost:8080/readme-demo   # the redirect itself stays public
```

`Ctrl-C` stops the app and the containers it started.

## API

| Method | Path                       | Success | Auth   | Notes                                     |
|--------|----------------------------|---------|--------|-------------------------------------------|
| `POST` | `/api/v1/url/shorten`      | `201`   | Bearer | `{url, customAlias?, hoursToExpire?}`     |
| `GET`  | `/{shortCode}`             | `302`   | —      | redirects and increments the click count  |
| `GET`  | `/api/v1/url/stats/{code}` | `200`   | Bearer | click stats, including for expired links  |
| `GET`  | `/api/v1/url/links`        | `200`   | Bearer | your links, paged by `page` and `size`    |
| `DELETE` | `/api/v1/url/links/{code}` | `204`  | Bearer | removes one of your links                 |
| `GET`  | `/actuator/health`         | `200`   | —      | health, including the database connection |

### Authentication

The API is a Keycloak resource server: it never sees a password and never starts a login, it only
validates the bearer token Keycloak issues. `POST /api/v1/url/shorten` and
`GET /api/v1/url/stats/{code}` require one; the redirect and the health endpoint stay open, so a
short link keeps working for whoever follows it.

Point the service at a realm with `KEYCLOAK_ISSUER_URI` (default `http://localhost:8081/realms/lynk`,
the realm `compose.yaml` imports). The issuer is read at startup, so Keycloak has to be reachable
before the app — which is why `spring-boot:run` starts it. A missing or invalid token is a `401` with
`type` `/problems/unauthenticated`, shaped like every other error.

### Ownership

A link belongs to the subject of the token that registered it, and every owner-scoped endpoint
answers for that subject alone. The two keyed by a single code —
`GET /api/v1/url/stats/{code}` and `DELETE /api/v1/url/links/{code}` — answer another caller with
the same `404` as an unknown code, so neither ever confirms that somebody else's link exists.
`GET /api/v1/url/links` has no single code to hide behind and simply returns an empty page (`200`
with `totalElements` 0) for links you do not own.

Short codes stay **globally** unique rather than being namespaced per user, because the redirect is
public and carries no user: `GET /{shortCode}` has to resolve a code to one destination on its own. So
a second person asking for a code you already hold still gets the `409`. Following a short link is
never scoped — anyone with the link can follow it, and the click is counted the same.

Links registered before ownership existed (the `V2` migration left them rather than deleting them)
keep redirecting, but they have no owner to match: their stats are unreadable to everyone and they
appear in nobody's listing.

### Interactive documentation

The OpenAPI 3.1 description is served at `/v3/api-docs` and rendered with Swagger UI at
`/swagger-ui.html`, so you can try the endpoints against a running instance:

```console
$ open http://localhost:8080/swagger-ui.html
```

Everything in the document is derived from the code: operations from the controllers, field
descriptions and examples from the DTOs, and the problem documents from `ApiExceptionHandler`. Two
things are worth knowing about it:

- `GET /{shortCode}` is deliberately **not** in the document. It is a route for browsers following a
  short link rather than an operation a client calls, and left in it would appear as a catch-all
  `/{shortCode}` that reads as "every other path".
- The problem-document responses are documented without example bodies. `type` is the stable handle
  to branch on, so the description names the possible types instead of showing a sample.
- The protected operations carry a `bearerAuth` requirement, so Swagger UI shows an **Authorize**
  button; paste a Keycloak access token there to call them.

Both endpoints are disabled under the `prod` profile — see [Configuration](#configuration).

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
`null` for a link that never expires. Both this endpoint and the one that created the link are scoped
to the link's owner — see [Ownership](#ownership).

### List your links

```console
$ curl -s 'http://localhost:8080/api/v1/url/links?page=0&size=20' \
    -H "Authorization: Bearer $TOKEN"
{
  "content": [
    {
      "originalUrl": "https://spring.io/guides",
      "shortUrl":    "http://localhost:8080/readme-demo",
      "createdAt":   "2026-09-28T05:09:39.484023Z",
      "expiresAt":   null,
      "clickCount":  12
    }
  ],
  "totalElements": 1
}
```

Newest first, and only your own links — the owner is part of the query, not a filter applied after
it, so another user's links can never fill a page. `page` is zero-based and `size` is capped at 100;
`totalElements` is how many there are to walk. A negative `page`, or a `size` outside `1..100`, is a
`400` `/problems/validation-failed` naming the offending field in `errors`.

This is a page at a time, not the whole set: one response never carries more than 100 links, however
many you own.

Each entry in `content` is exactly what `GET /api/v1/url/stats/{code}` returns for that link, so the
listing is the cheap way to walk everything you own.

### Delete a link

```console
$ curl -s -o /dev/null -w '%{http_code}\n' -X DELETE \
    http://localhost:8080/api/v1/url/links/readme-demo \
    -H "Authorization: Bearer $TOKEN"
204
```

Immediate and owner-scoped. The short code stops resolving — following it is a `404` from then on —
and the click count goes with it. Somebody else's link is a `404`, not a `403`, exactly as with the
stats endpoint.

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
| `401`  | `/problems/unauthenticated`   | no valid bearer token supplied          |
| `404`  | `/problems/url-not-found`     | no such short code, or one you do not own |
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
module finds `compose.yaml`, starts the database and a Keycloak carrying the `lynk` realm, wires the
datasource, and stops both again on shutdown.

```console
./mvnw spring-boot:run
```

The first run takes a little longer while Maven resolves dependencies and the database and Keycloak
images pull. The dev database publishes `5432` on a random host port so it cannot clash with a
PostgreSQL you already run; its data lives in the `lynk-dev-db` volume and survives restarts.
Keycloak publishes `8081` (the app keeps `8080`). Discard both with
`docker compose -f compose.yaml down -v`.

Keycloak takes some seconds longer than PostgreSQL to become usable, and the app discovers the
issuer's keys at startup — so on a cold first run it can fail before Keycloak is answering. Start
`spring-boot:run` again once `curl -s http://localhost:8081/realms/lynk` responds, and after that the
container is already warm.

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

JaCoCo runs on every build: `prepare-agent` instruments both suites into a single
`target/jacoco.exec`, `report` writes the merged HTML/XML/CSV report to `target/site/jacoco/`, and
`check` fails `./mvnw verify` if bundle coverage drops below line 80% / branch 70%, measured across
unit *and* integration tests together. `-Djacoco.skip=true` bypasses the gate.

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
| `KEYCLOAK_ISSUER_URI`           | (dev) `:8081/realms/lynk` | realm whose tokens the API accepts |

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
finish on shutdown. It also sets `springdoc.api-docs.enabled=false`, which takes the Swagger UI
down with it: production does not publish its API surface. `KEYCLOAK_ISSUER_URI` has no default
there, so the stack refuses to start without a realm to validate tokens against.

## Project layout

```
src/main/java/com/lynk/
  config/       @ConfigurationProperties record, the security filter chain, @EnableScheduling
  controller/   the two HTTP entry points: /api/v1/url/* and /{shortCode} (@Hidden from the OpenAPI doc)
  domain/       the UrlMapping entity
  dto/          request and response records — the entity never leaves the service
  error/        exceptions, the single @RestControllerAdvice, and the 401 entry point the filter chain needs
  repository/   Spring Data JPA interface, including the atomic click increment
  service/      shortening, resolution, validation, code generation, expiry sweep
src/main/resources/db/migration/   Flyway; the schema's only source of truth
src/test/java/com/lynk/            *Test = @Tag("unit"), *IT = @Tag("integration") via Testcontainers
src/test/resources/keycloak/       the lynk realm, imported by compose.yaml and the integration test
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

**The redirect is `302` with `Cache-Control: no-store`.** Both are required, and neither is
sufficient alone: a `301` is cached permanently by clients, so it would freeze the destination and
skip the click count forever, while a bare `302` is still heuristically cacheable by a proxy.

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

**The API is a resource server, not a client.** Keycloak mints the token; this service only
validates it against the realm's published keys, so no password ever reaches the app. Everything
under `/api/**` needs a token and everything else is public on purpose — the redirect must answer
strangers, and leaving unmatched paths public keeps a bad path a `404` rather than a confusing
`403`. The filter chain runs before MVC, so the `401` cannot go through `ApiExceptionHandler`; a
small `AuthenticationEntryPoint` writes the same problem document the rest of the API uses.

**A link is owned by the token's subject, and only stats are scoped by it.** The redirect is a route
strangers follow, so it stays public and short codes stay globally unique — ownership decides who may
*read* a link, not who may claim a code. Another owner's link answers the same `404` as an unknown
code rather than a `403`, because a `403` would confirm the code is real. The subject is read in the
controller, where the untrusted claim enters, and rejected there if absent: `sub` is optional in the
general JWT access-token profile, and registering a link under a subject-less token would create one
nobody — including its creator — could ever read back.

**`owner` is nullable on purpose.** `V2` could not make it `NOT NULL` without dropping links
that already existed, so pre-ownership rows keep a NULL owner, which matches no subject and therefore
reads as missing. That is the ceiling of the migration; the way out is to backfill or purge those
rows and then tighten the column.

**The listing sorts by `created_at` with the `id` as a tiebreaker.** Two links registered in the same
transaction share a timestamp, so `created_at` alone does not order a page deterministically: a page
boundary can fall between two indistinguishable rows and one appears on two pages or on none. The
index on `owner` serves both the listing and the owner-scoped stats read.

## Known limitations

These are deliberate scope decisions, not oversights:

- **No bulk operations.** Listing is paged and deletion takes one code, so clearing a long history
  means walking the pages and deleting link by link.
- **No roles or scopes.** Any valid token from the realm can call every protected endpoint;
  authorization stops at "is authenticated" plus link ownership.
- **No rate limiting** on `POST /api/v1/url/shorten`, so the holder of a valid token can create
  links without bound.
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
1. Create a feature branch from `develop`: `git switch -c feature/<feature-name>`
2. Make your changes and push them to your feature branch
3. Create a pull request targeting `develop` for features or `main` for hotfixes
4. Wait for CI tests to pass and get required approvals
5. Merge through the pull request (using squash or rebase)
6. Delete the feature branch: `git branch -d feature/<feature-name>`

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
