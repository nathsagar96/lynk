package ly.lynk.analytics;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import ly.lynk.click.ClickEntity;
import ly.lynk.click.ClickRepository;
import ly.lynk.exception.UrlNotFoundException;
import ly.lynk.exception.UrlOwnershipException;
import ly.lynk.url.UrlEntity;
import ly.lynk.url.UrlRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final UrlRepository urlRepository;
    private final ClickRepository clickRepository;

    @Transactional(readOnly = true)
    public AnalyticsResponse getAnalytics(String shortcode, Instant startDate, Instant endDate, String userId) {
        UrlEntity urlEntity =
                urlRepository.findByShortcode(shortcode).orElseThrow(() -> new UrlNotFoundException(shortcode));

        if (!urlEntity.getUserId().equals(userId)) {
            throw new UrlOwnershipException();
        }

        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("startDate cannot be after endDate");
        }

        List<ClickEntity> clicks = clickRepository.findByShortcodeAndClickedAtBetween(shortcode, startDate, endDate);

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
