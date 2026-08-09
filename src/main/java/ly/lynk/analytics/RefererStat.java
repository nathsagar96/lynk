package ly.lynk.analytics;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "HTTP referer click count breakdown")
public record RefererStat(
        @Schema(description = "HTTP referer header URL or 'Direct / None'", example = "https://google.com")
        String referer,

        @Schema(description = "Number of clicks from this referer", example = "80")
        long clicks) {}
