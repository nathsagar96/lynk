package ly.lynk.scheduler;

import ly.lynk.service.UrlCleanupService;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Scheduled trigger for the expired URL cleanup job. */
@Component
@ConditionalOnProperty(prefix = "app.cleanup", name = "enabled", havingValue = "true", matchIfMissing = true)
public class UrlCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(UrlCleanupScheduler.class);

    private final UrlCleanupService cleanupService;

    /**
     * Constructs a new cleanup scheduler.
     *
     * @param cleanupService the cleanup service to invoke
     */
    public UrlCleanupScheduler(UrlCleanupService cleanupService) {
        this.cleanupService = cleanupService;
    }

    /** Triggers the periodic expired URL purge. */
    @Scheduled(cron = "${app.cleanup.cron:0 0 * * * *}")
    @SchedulerLock(
            name = "UrlCleanupScheduler_purgeExpiredUrls",
            lockAtMostFor = "${app.cleanup.lock-at-most-for:10m}",
            lockAtLeastFor = "${app.cleanup.lock-at-least-for:1m}")
    public void scheduleCleanup() {
        log.debug("Starting scheduled expired URL cleanup");
        cleanupService.purgeExpiredUrls();
    }
}
