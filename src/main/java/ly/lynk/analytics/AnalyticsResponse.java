package ly.lynk.analytics;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

@Schema(description = "Short URL analytics response payload")
public record AnalyticsResponse(
        @Schema(description = "Unique shortcode identifier", example = "products")
        String shortcode,

        @Schema(description = "Query window start timestamp (ISO-8601)", example = "2026-07-10T14:20:00Z")
        Instant startDate,

        @Schema(description = "Query window end timestamp (ISO-8601)", example = "2026-08-09T14:20:00Z")
        Instant endDate,

        @Schema(description = "Total click count in the query window", example = "150")
        long totalClicks,

        @Schema(description = "Daily click counts time-series")
        List<TimeSeriesPoint> clicksOverTime,

        @Schema(description = "Top HTTP referer statistics (up to 10 entries)")
        List<RefererStat> topReferers,

        @Schema(description = "Browser family statistics") List<BrowserStat> topBrowsers,

        @Schema(description = "Operating system statistics") List<OsStat> topOs) {}
