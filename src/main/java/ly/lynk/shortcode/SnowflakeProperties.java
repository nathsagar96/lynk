package ly.lynk.shortcode;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "app.snowflake")
@Validated
public record SnowflakeProperties(
        @NotNull @Min(0) @Max(1023) Integer machineId,
        @NotNull Instant epoch) {}
