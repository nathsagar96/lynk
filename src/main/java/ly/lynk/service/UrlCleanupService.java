package ly.lynk.service;

import java.time.Clock;
import java.time.Instant;
import ly.lynk.config.UrlCleanupProperties;
import ly.lynk.repository.UrlMappingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Service responsible for purging expired URL mappings from persistence in bounded batches. */
@Service
public class UrlCleanupService {

    private static final Logger log = LoggerFactory.getLogger(UrlCleanupService.class);

    private final UrlMappingRepository repository;
    private final UrlCleanupProperties properties;
    private final Clock clock;

    /**
     * Constructs a new cleanup service.
     *
     * @param repository the URL mapping repository
     * @param properties configuration properties for cleanup batch sizes and limits
     * @param clock clock provider for reference time
     */
    public UrlCleanupService(UrlMappingRepository repository, UrlCleanupProperties properties, Clock clock) {
        this.repository = repository;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Purges expired URLs in batches up to configured limits.
     *
     * @return the total number of deleted URL mappings
     */
    public int purgeExpiredUrls() {
        Instant now = clock.instant();
        int totalDeleted = 0;
        int batchSize = properties.batchSize();
        int maxBatches = properties.maxBatchesPerRun();

        for (int batch = 0; batch < maxBatches; batch++) {
            int deleted = repository.deleteExpiredBatch(now, batchSize);
            totalDeleted += deleted;
            if (deleted < batchSize) {
                break;
            }
        }

        if (totalDeleted > 0) {
            log.info("Purged {} expired URL mappings", totalDeleted);
        } else {
            log.debug("No expired URL mappings found for purging");
        }

        return totalDeleted;
    }
}
