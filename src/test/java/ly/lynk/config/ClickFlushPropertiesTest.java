package ly.lynk.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ClickFlushPropertiesTest {

    @Test
    @DisplayName("preserves configured properties")
    void preservesConfiguredProperties() {
        ClickFlushProperties custom = new ClickFlushProperties(Duration.ofSeconds(10), 500);
        assertThat(custom.flushInterval()).isEqualTo(Duration.ofSeconds(10));
        assertThat(custom.batchSize()).isEqualTo(500);
    }
}
