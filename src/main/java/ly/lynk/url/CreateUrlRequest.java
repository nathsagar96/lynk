package ly.lynk.url;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import org.hibernate.validator.constraints.URL;
import org.hibernate.validator.constraints.time.DurationMax;
import org.hibernate.validator.constraints.time.DurationMin;

@Schema(description = "Request payload for creating a short URL")
public record CreateUrlRequest(
        @Schema(
                description = "The original URL to shorten",
                example = "https://docs.spring.io/spring-boot/reference/data/access.html#data.sql.jdbc-and-spring-data",
                maxLength = 2048)
        @NotBlank
        @URL
        @Pattern(regexp = "^(https?://).*", message = "URL must use http or https scheme")
        @Size(max = 2048)
        String url,

        @Schema(
                description = "Custom alias for the short URL (3-11 alphanumeric/hyphen chars). Omit to auto-generate.",
                example = "spring-data-docs",
                minLength = 3,
                maxLength = 11)
        @Size(min = 3, max = 11)
        @Pattern(
                regexp = "^[a-zA-Z0-9][a-zA-Z0-9-]{2,10}$",
                message =
                        "Alias must be 3-11 alphanumeric or hyphen characters, starting with an alphanumeric character")
        String alias,

        @Schema(
                description = "Time-to-live for the short URL (1s to 8760h). Omit for default (365 days).",
                example = "PT7D")
        @DurationMin(seconds = 1)
        @DurationMax(hours = 8760)
        Duration expiry) {}
