package ly.lynk.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ProfilesConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(TestConfig.class);

    @EnableConfigurationProperties(AppProperties.class)
    static class TestConfig {}

    @Test
    @DisplayName("local profile sets local defaults without structured logging")
    void localProfileSetsLocalDefaults() {
        contextRunner.withPropertyValues("spring.profiles.active=local").run(context -> {
            assertThat(context.getEnvironment().getProperty("spring.security.oauth2.resourceserver.jwt.issuer-uri"))
                    .isEqualTo("http://localhost:8180/realms/lynk");
            assertThat(context.getEnvironment().getProperty("management.tracing.sampling.probability"))
                    .isEqualTo("1.0");
            assertThat(context.getEnvironment().getProperty("spring.datasource.hikari.maximum-pool-size"))
                    .isEqualTo("5");
            assertThat(context.getEnvironment().getProperty("spring.datasource.hikari.minimum-idle"))
                    .isEqualTo("1");
            assertThat(context.getEnvironment().getProperty("logging.structured.format.console"))
                    .isNull();

            AppProperties appProperties = context.getBean(AppProperties.class);
            assertThat(appProperties.baseUrl()).isEqualTo("http://localhost:8080");
            assertThat(appProperties.reservedAliases()).containsExactlyInAnyOrder("api", "actuator", "error");
        });
    }

    @Test
    @DisplayName("prod profile sets production defaults with structured logging and reduced sampling")
    void prodProfileSetsProdDefaults() {
        contextRunner
                .withPropertyValues(
                        "spring.profiles.active=prod",
                        "APP_BASE_URL=https://lynk.dev",
                        "APP_CORS_ALLOWED_ORIGINS=https://lynk.dev",
                        "KEYCLOAK_ISSUER_URI=https://auth.lynk.dev/realms/lynk")
                .run(context -> {
                    assertThat(context.getEnvironment().getProperty("server.port"))
                            .isEqualTo("8080");
                    assertThat(context.getEnvironment().getProperty("server.forward-headers-strategy"))
                            .isEqualTo("framework");
                    assertThat(context.getEnvironment().getProperty("app.base-url"))
                            .isEqualTo("https://lynk.dev");
                    assertThat(context.getEnvironment().getProperty("logging.structured.format.console"))
                            .isEqualTo("ecs");
                    assertThat(context.getEnvironment().getProperty("management.tracing.sampling.probability"))
                            .isEqualTo("0.01");
                    assertThat(context.getEnvironment().getProperty("spring.datasource.hikari.maximum-pool-size"))
                            .isEqualTo("25");

                    AppProperties appProperties = context.getBean(AppProperties.class);
                    assertThat(appProperties.baseUrl()).isEqualTo("https://lynk.dev");
                });
    }
}
