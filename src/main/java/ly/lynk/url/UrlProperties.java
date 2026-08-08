package ly.lynk.url;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "app.url")
@Validated
public record UrlProperties(
        @Valid @NotNull Duration defaultExpiry,
        @Valid @NotNull Cleanup cleanup) {

    public record Cleanup(@Positive int batchSize) {}
}
