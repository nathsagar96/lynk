package ly.lynk.common;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.time.Instant;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "lynk")
@Validated
public record LynkProperties(
        @Valid SnowflakeProperties snowflake, @Valid UrlProperties url) {

    @ConfigurationProperties(prefix = "lynk.snowflake")
    @Validated
    public record SnowflakeProperties(
            @NotNull @Min(0) @Max(1023) Integer machineId,
            @NotNull Instant epoch) {}

    public record UrlProperties(@NotNull Duration defaultExpiry) {}
}
