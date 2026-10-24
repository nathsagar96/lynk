## Project overview

- `Lynk` is a Java 25 / Spring Boot 4.1 application.
- Persistence uses Spring Data JPA with Hibernate and PostgreSQL.

## Project structure

- Production sources live in `src/main/java`.
- Code is organized by technical layer (`controller`, `service`, `repository`, `model`).
- Application configuration lives in `src/main/resources/application.properties`.
- Make database schema changes only through Flyway migrations in `src/main/resources/db/migration` directory.
- Never edit an already-applied migration: Flyway validates checksums at startup and fails with "Migration checksum
  mismatch". Add a new versioned migration instead.

## Development

- The build uses Maven; always use the checked-in `./mvnw` wrapper.
- Docker Compose supplies local development services (PostgreSQL and Keycloak) and is invoked when the application starts.
- The datasource is configured by Spring Boot's Docker Compose support from `compose.yaml`; there is no explicit
  `spring.datasource.*` entry in `application.properties`, so `SPRING_DATASOURCE_*` environment variables do not
  override it.
- Authentication uses OAuth2 Resource Server with JWTs; tests mock `JwtDecoder` to remain fast and offline.
- `@WebMvcTest` slices that import `SecurityConfig` must register `AppProperties` themselves (see
  `TestSecurityConfiguration`), because slice contexts skip the application's `@ConfigurationPropertiesScan`.
- Run `./mvnw clean verify` for the full suite: `*Test` classes run under Surefire, `*IT` classes run under Failsafe
  against a PostgreSQL Testcontainer.
- Spotless (Palantir Java Format) runs as part of `verify`; use `./mvnw spotless:apply` to fix formatting.

Run from the repository root:

```shell
./mvnw spring-boot:run
./mvnw test
./mvnw package
```

## Working conventions

- Use constructor injection and follow adjacent Java code.
- Run the narrowest meaningful verification while iterating.
- Keep changes task-scoped and preserve unrelated existing changes. When a required file is already modified, integrate
  with those changes.
- Never commit production credentials. Keep secrets in environment variables or local, unversioned overrides.
- Add concise, verifiable repository-wide guidance discovered during a task to `AGENTS.md`; omit task details and
  duplicate documentation.
