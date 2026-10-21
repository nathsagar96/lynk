## Project overview

- `Lynk` is a Java 25 / Spring Boot 4.1 application.
- Persistence uses Spring Data JPA with Hibernate and PostgreSQL.

## Project structure

- Production sources live in `src/main/java`.
- Application configuration lives in `src/main/resources/application.properties`.
- Make database schema changes only through Flyway migrations in `src/main/resources/db/migration` directory.

## Development

- The build uses Maven; always use the checked-in `./mvnw` wrapper.
- Docker Compose supplies local development services and is invoked when the application starts.

Run from the repository root:

```shell
./mvnw spring-boot:run
./mvnw test
./mvnw package
```

## Working conventions

- Use constructor injection and follow adjacent Java code.
- Run the narrowest meaningful verification while iterating.
- Keep changes task-scoped and preserve unrelated existing changes. When a required file is already modified, integrate with those changes.
- Never commit production credentials. Keep secrets in environment variables or local, unversioned overrides.
- Add concise, verifiable repository-wide guidance discovered during a task to `AGENTS.md`; omit task details and duplicate documentation.
