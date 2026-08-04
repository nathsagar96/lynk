package ly.lynk.url;

import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ly.lynk.click.ClickEvent;
import ly.lynk.common.exception.UrlExpiredException;
import ly.lynk.common.exception.UrlNotFoundException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class RedirectService {

    private final UrlRepository urlRepository;
    private final UrlCacheService urlCacheService;
    private final ApplicationEventPublisher eventPublisher;

    public String resolveAndTrack(String shortcode, String ipAddress, String userAgent, String referer) {
        String originalUrl = urlCacheService.getCachedUrl(shortcode).orElseGet(() -> resolveFromDb(shortcode));

        eventPublisher.publishEvent(new ClickEvent(shortcode, ipAddress, userAgent, referer, Instant.now()));

        return originalUrl;
    }

    @Transactional(readOnly = true)
    private String resolveFromDb(String shortcode) {
        UrlEntity entity =
                urlRepository.findByShortcode(shortcode).orElseThrow(() -> new UrlNotFoundException(shortcode));

        if (entity.getExpiresAt().isBefore(Instant.now())) {
            throw new UrlExpiredException(shortcode);
        }

        Duration remainingTtl = Duration.between(Instant.now(), entity.getExpiresAt());
        urlCacheService.cacheUrl(shortcode, entity.getOriginalUrl(), remainingTtl);

        return entity.getOriginalUrl();
    }
}
