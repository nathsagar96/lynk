package ly.lynk.analytics;

import java.time.Instant;
import java.util.List;
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
    private final AnalyticsMapper analyticsMapper;

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

        return analyticsMapper.toResponse(shortcode, startDate, endDate, clicks);
    }
}
