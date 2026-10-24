package ly.lynk.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UrlCleanupPropertiesTest {

    @Test
    @DisplayName("preserves configured properties when provided")
    void preservesConfiguredProperties() {
        UrlCleanupProperties properties = new UrlCleanupProperties(500, 20);

        assertThat(properties.batchSize()).isEqualTo(500);
        assertThat(properties.maxBatchesPerRun()).isEqualTo(20);
    }
}
