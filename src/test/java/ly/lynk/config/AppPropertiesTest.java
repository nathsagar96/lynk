package ly.lynk.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AppPropertiesTest {

    @Test
    @DisplayName("defaults baseUrl to http://localhost:8080 when null")
    void defaultsBaseUrlWhenNull() {
        AppProperties properties = new AppProperties(null, 7, 3, null, null, null);

        assertThat(properties.baseUrl()).isEqualTo("http://localhost:8080");
        assertThat(properties.reservedAliases()).containsExactlyInAnyOrder("api", "actuator", "error");
        assertThat(properties.corsAllowedOrigins())
                .containsExactly("http://localhost:3000", "http://localhost:5173", "http://localhost:8080");
    }

    @Test
    @DisplayName("preserves configured corsAllowedOrigins when provided")
    void preservesProvidedCorsOrigins() {
        AppProperties properties =
                new AppProperties(null, 7, 3, null, List.of("https://lynk.app", "https://preview.lynk.app"), null);

        assertThat(properties.corsAllowedOrigins()).containsExactly("https://lynk.app", "https://preview.lynk.app");
    }

    @Test
    @DisplayName("strips trailing slashes and whitespace from baseUrl")
    void normalizesBaseUrlTrailingSlashes() {
        AppProperties properties = new AppProperties("  https://lynk.dev///  ", 7, 3, Set.of("custom"), null, null);

        assertThat(properties.baseUrl()).isEqualTo("https://lynk.dev");
        assertThat(properties.reservedAliases()).containsExactly("custom");
    }

    @Test
    @DisplayName("preserves configured properties when provided")
    void preservesProvidedProperties() {
        AppProperties properties = new AppProperties("https://my.domain", 8, 5, Set.of("admin", "sys"), null, null);

        assertThat(properties.baseUrl()).isEqualTo("https://my.domain");
        assertThat(properties.defaultAliasLength()).isEqualTo(8);
        assertThat(properties.maxRetryAttempts()).isEqualTo(5);
        assertThat(properties.reservedAliases()).containsExactlyInAnyOrder("admin", "sys");
        assertThat(properties.cacheTtl()).isEqualTo(Duration.ofHours(1));
    }

    @Test
    @DisplayName("preserves custom cacheTtl when provided")
    void preservesCustomCacheTtl() {
        AppProperties properties =
                new AppProperties("https://my.domain", 8, 5, Set.of("admin"), null, Duration.ofMinutes(30));

        assertThat(properties.cacheTtl()).isEqualTo(Duration.ofMinutes(30));
    }
}
