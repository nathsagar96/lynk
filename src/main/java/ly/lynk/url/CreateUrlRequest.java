package ly.lynk.url;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import org.hibernate.validator.constraints.URL;
import org.hibernate.validator.constraints.time.DurationMax;
import org.hibernate.validator.constraints.time.DurationMin;

public record CreateUrlRequest(
        @NotBlank
        @URL
        @Pattern(regexp = "^(https?://).*", message = "URL must use http or https scheme")
        @Size(max = 2048)
        String url,

        @Size(min = 3, max = 11)
        @Pattern(
                regexp = "^[a-zA-Z0-9][a-zA-Z0-9-]{2,10}$",
                message =
                        "Alias must be 3-11 alphanumeric or hyphen characters, starting with an alphanumeric character")
        String alias,

        @DurationMin(seconds = 1) @DurationMax(hours = 8760) Duration expiry) {}
