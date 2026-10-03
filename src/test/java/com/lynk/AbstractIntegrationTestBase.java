package com.lynk;

import static org.mockito.Mockito.mock;

import com.lynk.repository.UrlMappingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** Base for tests that need a real PostgreSQL with Flyway migrations applied. */
@Tag("integration")
@Import({AbstractIntegrationTestBase.Postgres.class, AbstractIntegrationTestBase.Security.class})
@SpringBootTest
@TestPropertySource(properties = "lynk.base-url=http://localhost:8080")
public abstract class AbstractIntegrationTestBase {

    @Autowired
    protected UrlMappingRepository repository;

    @BeforeEach
    void clearMappings() {
        repository.deleteAll();
        repository.flush();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Postgres {

        @Bean
        @ServiceConnection
        PostgreSQLContainer postgresContainer() {
            return new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"));
        }
    }

    /**
     * Keeps every suite offline: a {@link JwtDecoder} built from the configured issuer-uri fetches
     * the realm's metadata at startup, which would demand a live Keycloak here. The mocked decoder
     * satisfies the resource server's wiring without one — the request post-processor authenticates
     * without it, and {@code KeycloakAuthenticationIT} proves decoding against a real Keycloak.
     */
    @TestConfiguration(proxyBeanMethods = false)
    static class Security {

        @Bean
        JwtDecoder jwtDecoder() {
            return mock(JwtDecoder.class);
        }
    }
}
