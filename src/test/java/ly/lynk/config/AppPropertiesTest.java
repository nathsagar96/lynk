package ly.lynk.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AppPropertiesTest {

    @Test
    @DisplayName("defaults baseUrl to http://localhost:8080 when null")
    void defaultsBaseUrlWhenNull() {
        AppProperties properties = new AppProperties(null, 7, 3, null);

        assertThat(properties.baseUrl()).isEqualTo("http://localhost:8080");
        assertThat(properties.reservedAliases()).containsExactlyInAnyOrder("api", "actuator", "error");
    }

    @Test
    @DisplayName("strips trailing slashes and whitespace from baseUrl")
    void normalizesBaseUrlTrailingSlashes() {
        AppProperties properties = new AppProperties("  https://lynk.dev///  ", 7, 3, Set.of("custom"));

        assertThat(properties.baseUrl()).isEqualTo("https://lynk.dev");
        assertThat(properties.reservedAliases()).containsExactly("custom");
    }

    @Test
    @DisplayName("preserves configured properties when provided")
    void preservesProvidedProperties() {
        AppProperties properties = new AppProperties("https://my.domain", 8, 5, Set.of("admin", "sys"));

        assertThat(properties.baseUrl()).isEqualTo("https://my.domain");
        assertThat(properties.defaultAliasLength()).isEqualTo(8);
        assertThat(properties.maxRetryAttempts()).isEqualTo(5);
        assertThat(properties.reservedAliases()).containsExactlyInAnyOrder("admin", "sys");
    }
}
