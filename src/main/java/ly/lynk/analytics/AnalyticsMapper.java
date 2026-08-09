package ly.lynk.analytics;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import ly.lynk.click.ClickEntity;
import org.springframework.stereotype.Component;

@Component
public class AnalyticsMapper {

    public AnalyticsResponse toResponse(
            String shortcode, Instant startDate, Instant endDate, List<ClickEntity> clicks) {
        long totalClicks = clicks.size();

        List<TimeSeriesPoint> clicksOverTime = clicks.stream()
                .collect(Collectors.groupingBy(
                        c -> c.getClickedAt().atZone(ZoneOffset.UTC).toLocalDate(), Collectors.counting()))
                .entrySet()
                .stream()
                .map(e -> new TimeSeriesPoint(e.getKey(), e.getValue()))
                .sorted(Comparator.comparing(TimeSeriesPoint::date))
                .toList();

        List<RefererStat> topReferers = clicks.stream()
                .map(c -> (c.getReferer() == null || c.getReferer().isBlank()) ? "Direct / None" : c.getReferer())
                .collect(Collectors.groupingBy(r -> r, Collectors.counting()))
                .entrySet()
                .stream()
                .map(e -> new RefererStat(e.getKey(), e.getValue()))
                .sorted(Comparator.comparingLong(RefererStat::clicks).reversed())
                .limit(10)
                .toList();

        List<BrowserStat> topBrowsers = clicks.stream()
                .map(c -> UserAgentParser.parseBrowser(c.getUserAgent()))
                .collect(Collectors.groupingBy(b -> b, Collectors.counting()))
                .entrySet()
                .stream()
                .map(e -> new BrowserStat(e.getKey(), e.getValue()))
                .sorted(Comparator.comparingLong(BrowserStat::clicks).reversed())
                .toList();

        List<OsStat> topOs = clicks.stream()
                .map(c -> UserAgentParser.parseOs(c.getUserAgent()))
                .collect(Collectors.groupingBy(o -> o, Collectors.counting()))
                .entrySet()
                .stream()
                .map(e -> new OsStat(e.getKey(), e.getValue()))
                .sorted(Comparator.comparingLong(OsStat::clicks).reversed())
                .toList();

        return new AnalyticsResponse(
                shortcode, startDate, endDate, totalClicks, clicksOverTime, topReferers, topBrowsers, topOs);
    }
}
