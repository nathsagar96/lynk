# Multi-stage build: resolve and compile with Maven, then ship only the runtime.
# Uses the Maven wrapper, so the build matches ./mvnw locally.

FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /workspace

# Dependencies first, so a source-only change does not re-resolve the whole tree.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw -B -q dependency:go-offline

COPY src/ src/
RUN ./mvnw -B -q clean package -DskipTests


FROM eclipse-temurin:25-jre-alpine AS runtime
WORKDIR /app

# Run unprivileged.
RUN addgroup -S lynk && adduser -S lynk -G lynk
USER lynk

COPY --from=build /workspace/target/lynk-*.jar app.jar

EXPOSE 8080

# Uses the actuator health endpoint, so the container reports unhealthy if the app or its
# database connection is broken.
HEALTHCHECK --interval=30s --timeout=3s --start-period=45s --retries=3 \
    CMD wget --quiet --tries=1 --spider http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
