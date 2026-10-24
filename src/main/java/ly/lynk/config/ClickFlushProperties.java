package ly.lynk.config;

import jakarta.validation.constraints.Min;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for the asynchronous click batch flusher.
 *
 * @param flushInterval interval duration between periodic batch flush runs
 * @param batchSize maximum number of updates to execute per batch
 */
@Validated
@ConfigurationProperties(prefix = "app.clicks")
public record ClickFlushProperties(
        Duration flushInterval, @Min(1) int batchSize) {

    public ClickFlushProperties {
        if (flushInterval == null) {
            flushInterval = Duration.ofSeconds(5);
        }
    }
}
