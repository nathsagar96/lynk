package ly.lynk.url;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import org.hibernate.validator.constraints.URL;

public record CreateUrlRequest(
        @NotBlank @URL String url,

        @Size(min = 3, max = 11) @Pattern(regexp = "^[a-zA-Z0-9-]+$", message = "Alias must be alphanumeric or hyphens")
        String alias,

        Duration expiry) {}
