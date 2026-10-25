# Lynk


Lynk is a high-performance URL shortener service built with Java 25 and Spring Boot 4.1. It is developer-facing and requires configuration (database, Redis, Keycloak) before use, but ships with Docker Compose for local development and a CI pipeline for builds.

## Noteworthy features
- Short URL creation with auto-generated or custom aliases
- Redirects with click tracking (HEAD requests do not count)
- Click statistics per alias
- Expiry support with scheduled cleanup of expired URLs
- QR code generation (PNG) for short links
- Distributed scheduling via ShedLock
- Caching with Caffeine + Redis
- OAuth2 / JWT security (Keycloak)
- Observability via Actuator + Prometheus metrics
- Flyway-managed PostgreSQL schema
- CI builds, tests, and Docker image via GitHub Actions

## Goals
Provide a production-grade, self-hostable URL shortener that demonstrates modern Spring Boot patterns.

## Key concepts
- **Alias**: short token mapping to an original URL.
- **ShedLock**: ensures scheduled jobs run on a single instance in a cluster.
- **Testcontainers**: integration tests spin up real Postgres + Redis.

## Links
- Repository: https://github.com/nathsagar96/lynk
- CI: https://github.com/nathsagar96/lynk/actions

## Development status
Active development (0.0.8-SNAPSHOT).

## Getting started

### Prerequisites
- Java 25
- Maven (or `./mvnw`)
- Docker (PostgreSQL, Redis, Keycloak via Compose)

### Getting it
```shell
git clone https://github.com/nathsagar96/lynk.git
cd lynk
```

### Running
```shell
./mvnw spring-boot:run
```
Spring Boot's Docker Compose support starts PostgreSQL, Redis, and Keycloak automatically.

### Tests
```shell
./mvnw test          # unit tests
./mvnw verify        # full suite incl. integration tests
```

### Build
```shell
./mvnw package
./mvnw spring-boot:build-image   # Docker image
```

## Configuration
See `src/main/resources/application.properties` and `application-prod.properties`. Key env vars: `APP_BASE_URL`, `APP_CORS_ALLOWED_ORIGINS`, `DB_POOL_MAX_SIZE`.

## Common errors
- Migration checksum mismatch: never edit applied Flyway migrations; add a new one.
- Missing Docker: integration tests require Docker for Testcontainers.

## Contributing
- Follow Palantir Java Format (Spotless). Run `./mvnw spotless:apply`.
- Use constructor injection.
- Commit messages: concise, imperative.

## TODO
- Publish Docker image to registry
- Add more integration coverage

## Contact
- Issues: https://github.com/nathsagar96/lynk/issues

## License
MIT — see [LICENSE](LICENSE).
