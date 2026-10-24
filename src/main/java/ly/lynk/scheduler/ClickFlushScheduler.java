package ly.lynk.scheduler;

import ly.lynk.service.ClickFlushService;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Scheduled trigger for periodically flushing buffered URL clicks to PostgreSQL. */
@Component
@ConditionalOnProperty(prefix = "app.clicks", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ClickFlushScheduler {

    private static final Logger log = LoggerFactory.getLogger(ClickFlushScheduler.class);

    private final ClickFlushService clickFlushService;

    /**
     * Constructs a click flush scheduler.
     *
     * @param clickFlushService the click flush service to invoke
     */
    public ClickFlushScheduler(ClickFlushService clickFlushService) {
        this.clickFlushService = clickFlushService;
    }

    /** Triggers the periodic flush of buffered clicks from Redis to PostgreSQL. */
    @Scheduled(fixedDelayString = "${app.clicks.flush-interval:5s}")
    @SchedulerLock(
            name = "ClickFlushScheduler_flushClicks",
            lockAtMostFor = "${app.clicks.lock-at-most-for:1m}",
            lockAtLeastFor = "${app.clicks.lock-at-least-for:1s}")
    public void scheduleFlush() {
        log.debug("Starting scheduled click buffer flush");
        clickFlushService.flushPendingClicks();
    }
}
