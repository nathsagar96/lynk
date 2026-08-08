package ly.lynk.url;

import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExpiredUrlCleanupScheduler {

    private final UrlRepository urlRepository;
    private final UrlCacheService urlCacheService;
    private final UrlProperties urlProperties;
    private final Clock clock;

    @Scheduled(cron = "0 0 1 * * ?")
    @Transactional
    public void deleteExpiredUrls() {
        Instant now = clock.instant();
        int batchSize = urlProperties.cleanup().batchSize();
        long totalDeleted = 0;

        while (true) {
            var expiredUrls =
                    urlRepository.findTopByExpiresAtBeforeOrderByExpiresAtAsc(now, PageRequest.of(0, batchSize));

            if (expiredUrls.isEmpty()) {
                break;
            }

            for (UrlEntity url : expiredUrls) {
                urlCacheService.evict(url.getShortcode());
            }

            long deleted = urlRepository.deleteByExpiresAtBefore(now);
            totalDeleted += deleted;

            if (deleted < batchSize) {
                break;
            }
        }

        if (totalDeleted > 0) {
            log.info("Cleaned up {} expired URLs", totalDeleted);
        }
    }
}
