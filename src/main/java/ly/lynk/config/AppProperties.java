package ly.lynk.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Application configuration properties for the Lynk URL shortener.
 *
 * <p>The public short-URL host is taken exclusively from {@link #baseUrl()};
 * the incoming request host is never consulted. Configure it with
 * {@code app.base-url} (or the {@code APP_BASE_URL} environment variable);
 * a blank value fails startup.
 */
@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        @NotBlank(message = "app.base-url must not be blank")
        String baseUrl,

        @Min(1) int defaultAliasLength,

        @Min(1) int maxRetryAttempts,

        Set<String> reservedAliases) {

    public AppProperties {
        if (baseUrl == null) {
            baseUrl = "http://localhost:8080";
        } else {
            baseUrl = baseUrl.trim();
            while (baseUrl.endsWith("/")) {
                baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
            }
        }
        if (reservedAliases == null) {
            reservedAliases = Set.of("api", "actuator", "error");
        }
    }
}
