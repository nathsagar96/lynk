# Lynk

Lynk is a production-minded URL shortener API built with Spring Boot. It lets authenticated users create and manage short links, while public short links redirect to their destinations and record privacy-conscious click events.

## Features

- Create short links with generated Base62 codes or custom aliases
- Set an expiry for every link (365 days by default)
- Manage only the links owned by the authenticated user
- Resolve public links with HTTP 302 redirects
- Cache resolved destinations in Redis
- Track clicks asynchronously and anonymize IP addresses before persistence
- Retrieve click analytics (total clicks, time-series, referers, browser/OS breakdowns) for short links
- Remove expired links in scheduled batches
- Expose OpenAPI documentation, health checks, metrics, Prometheus metrics, and tracing hooks

## Tech stack

- Java 25 and Spring Boot 4
- PostgreSQL with Flyway migrations
- Redis for URL-resolution caching
- ShedLock for distributed scheduled-task coordination across pods
- Keycloak and JWT bearer authentication
- Springdoc OpenAPI / Swagger UI
- Maven Wrapper, Testcontainers, and Spotless

## Quick start

### Prerequisites

- Docker and Docker Compose
- JDK 25

### Clone the repository

```bash
git clone git@github.com:nathsagar96/lynk.git
cd lynk
```

### Start local infrastructure

The included Compose file starts PostgreSQL, Redis, and Keycloak. The Keycloak realm and `lynk-app` client are imported automatically.

```bash
docker compose up -d
```

Service endpoints:

| Service | Address |
| --- | --- |
| Application | `http://localhost:8080` |
| PostgreSQL | `localhost:5432` |
| Redis | `localhost:6379` |
| Keycloak | `http://localhost:8081` |

Run the application with the development profile:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

On startup, Flyway creates the required database schema. The API reference is available at [Swagger UI](http://localhost:8080/swagger-ui.html), and the OpenAPI document is at `http://localhost:8080/v3/api-docs`.

### Build a Docker image

Spring Boot's buildpacks support builds an OCI image without a Dockerfile. A running Docker daemon is required.

```bash
./mvnw spring-boot:build-image
```

The image is tagged as `ly.lynk:0.0.1-SNAPSHOT` by default. Override the name with:

```bash
./mvnw spring-boot:build-image -Dspring-boot.build-image.imageName=lynk:latest
```

Run the container with:

```bash
docker run -p 8080:8080 \
  -e KEYCLOAK_ISSUER_URI=<issuer-uri> \
  -e DB_URL=<jdbc-url> \
  -e DB_USERNAME=<user> \
  -e DB_PASSWORD=<password> \
  -e REDIS_HOST=<host> \
  ly.lynk:0.0.1-SNAPSHOT
```

To build the image automatically during `mvn package`, bind the goal in `pom.xml`:

```xml
<plugin>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-maven-plugin</artifactId>
    <executions>
        <execution>
            <goals>
                <goal>build-image-no-fork</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

## Authentication

Management endpoints under `/api/v1/urls` require a Keycloak-issued JWT. Public redirects at `/{shortcode}` do not.

The local Keycloak realm includes the public client `lynk-app` with direct-access grants enabled. Create a user in the local Keycloak admin console, then request a token:

```bash
curl -X POST http://localhost:8081/realms/lynk/protocol/openid-connect/token \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  -d 'client_id=lynk-app' \
  -d 'grant_type=password' \
  -d 'username=<username>' \
  -d 'password=<password>'
```

Use the returned `access_token` as a bearer token in API requests.

## API usage

### Create a short link

```bash
curl -X POST http://localhost:8080/api/v1/urls \
  -H "Authorization: Bearer <access-token>" \
  -H 'Content-Type: application/json' \
  -d '{
    "url": "https://example.com/products",
    "alias": "products",
    "expiry": "P7D"
  }'
```

`alias` is optional and must be 3–11 characters: letters, numbers, and hyphens, beginning with a letter or number. When it is omitted, Lynk generates a shortcode. `expiry` is an ISO-8601 duration from one second to 8760 hours; it defaults to 365 days.

Example response:

```json
{
  "shortcode": "products",
  "originalUrl": "https://example.com/products",
  "expiresAt": "2026-08-16T10:15:32Z",
  "createdAt": "2026-08-09T10:15:32Z"
}
```

### Manage links

All of these endpoints require `Authorization: Bearer <access-token>`.

| Method | Endpoint | Description |
| --- | --- | --- |
| `GET` | `/api/v1/urls` | List the caller’s links (supports Spring Data pagination parameters such as `page`, `size`, and `sort`) |
| `GET` | `/api/v1/urls/{shortcode}` | Retrieve a link owned by the caller |
| `GET` | `/api/v1/urls/{shortcode}/analytics` | Retrieve click analytics, time-series data, referer, browser, and OS breakdowns for a link owned by the caller |
| `DELETE` | `/api/v1/urls/{shortcode}` | Permanently delete a link owned by the caller |
| `GET` | `/{shortcode}` | Publicly redirect to the destination with `302 Found` |

### Retrieve link analytics

```bash
curl -X GET "http://localhost:8080/api/v1/urls/products/analytics?startDate=2026-07-09T00:00:00Z&endDate=2026-08-09T23:59:59Z" \
  -H "Authorization: Bearer <access-token>"
```

`startDate` and `endDate` are optional ISO-8601 timestamps. `startDate` defaults to 30 days prior to `endDate` (which defaults to the current time).

Example response:

```json
{
  "shortcode": "products",
  "startDate": "2026-07-10T14:20:00Z",
  "endDate": "2026-08-09T14:20:00Z",
  "totalClicks": 150,
  "clicksOverTime": [
    { "date": "2026-08-08", "clicks": 25 },
    { "date": "2026-08-09", "clicks": 42 }
  ],
  "topReferers": [
    { "referer": "https://google.com", "clicks": 80 },
    { "referer": "Direct / None", "clicks": 70 }
  ],
  "topBrowsers": [
    { "browser": "Chrome", "clicks": 100 },
    { "browser": "Safari", "clicks": 50 }
  ],
  "topOs": [
    { "os": "macOS", "clicks": 90 },
    { "os": "iOS", "clicks": 60 }
  ]
}
```

Invalid requests and domain errors are returned as RFC 7807 Problem Details responses (`application/problem+json`).

## Configuration

The default configuration lives in `src/main/resources/application.yaml`. Important environment variables include:

| Variable | Purpose | Default |
| --- | --- | --- |
| `KEYCLOAK_ISSUER_URI` | JWT issuer URL | Required outside the `dev` profile |
| `MACHINE_ID` | Snowflake generator machine identifier | `0` |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | PostgreSQL connection in `prod` | Required in `prod` |
| `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD` | Redis connection in `prod` | Required in `prod` |
| `ENVIRONMENT` | Structured-log environment name | `production` |
| `TRACING_SAMPLING_PROBABILITY` | Fraction of traces sampled in production | `0.1` |

The `dev` profile enables SQL and request logging, Swagger UI, and local Keycloak at `http://localhost:8081/realms/lynk`. The `prod` profile disables Compose integration and Swagger/OpenAPI exposure, uses graceful shutdown, and expects external PostgreSQL and Redis configuration.

## Operations

Actuator exposes the following endpoints:

- `/actuator/health`
- `/actuator/info`
- `/actuator/metrics`
- `/actuator/prometheus`

In production, health is public while the remaining actuator endpoints require authentication.

## Architecture

```text
Authenticated client ──JWT──> URL management API ──> PostgreSQL
                                       │
                                       └─────────────> Redis cache

Public visitor ──> /{shortcode} ──> Redis / PostgreSQL ──> 302 redirect
                                       │
                                       └─────────────> async click buffer ──> PostgreSQL

Scheduled jobs ──> ShedLock (PostgreSQL) ──> only one pod executes cleanup at a time
```

URL ownership is determined from the JWT subject. Resolved destinations are cached for their remaining link lifetime. Click events are queued asynchronously, persisted in batches, and have their IPv4/IPv6 addresses anonymized before storage. Expired-URL cleanup uses ShedLock to prevent concurrent execution across multiple pods.

## Development

Run the test suite:

```bash
./mvnw verify
```

Run the formatter check:

```bash
./mvnw spotless:check
```

Apply formatting when needed:

```bash
./mvnw spotless:apply
```

Integration tests use Testcontainers, so Docker must be available when running the full test suite.

## Contributing

1. Create a focused branch from the latest main branch.
2. Keep changes covered by relevant unit or integration tests.
3. Run `./mvnw verify` and `./mvnw spotless:check` before opening a pull request.
4. Describe the behavior change, validation performed, and any configuration or migration impact in the pull request.

## License

This project is licensed under the [MIT License](LICENSE).
