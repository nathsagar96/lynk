package ly.lynk.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Service for asynchronously recording URL click events in a Redis atomic hash buffer.
 *
 * <p>Fails open if Redis is temporarily unreachable so that redirects continue to succeed.
 */
@Service
public class ClickTrackingService {

    private static final Logger log = LoggerFactory.getLogger(ClickTrackingService.class);

    /** Redis hash key holding pending click increments by alias. */
    public static final String PENDING_CLICKS_KEY = "lynk:clicks:pending";

    private final StringRedisTemplate redisTemplate;

    /**
     * Constructs a click tracking service backed by Redis.
     *
     * @param redisTemplate the string Redis template
     */
    public ClickTrackingService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Atomically increments the pending click count for the specified alias.
     *
     * @param alias the URL alias that was clicked
     */
    public void recordClick(String alias) {
        try {
            redisTemplate.opsForHash().increment(PENDING_CLICKS_KEY, alias, 1);
        } catch (RuntimeException e) {
            log.warn("Failed to buffer click in Redis for alias={}: {}", alias, e.getMessage());
        }
    }
}
