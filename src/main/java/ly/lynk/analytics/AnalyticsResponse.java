package ly.lynk.analytics;

import java.time.Instant;
import java.util.List;

public record AnalyticsResponse(
        String shortcode,
        Instant startDate,
        Instant endDate,
        long totalClicks,
        List<TimeSeriesPoint> clicksOverTime,
        List<RefererStat> topReferers,
        List<BrowserStat> topBrowsers,
        List<OsStat> topOs) {}
