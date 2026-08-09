package ly.lynk.analytics;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Browser family click count breakdown")
public record BrowserStat(
        @Schema(
                description = "Parsed browser name (e.g. Chrome, Safari, Firefox, Edge, Opera, Other, Unknown)",
                example = "Chrome")
        String browser,

        @Schema(description = "Number of clicks from this browser", example = "100")
        long clicks) {}
