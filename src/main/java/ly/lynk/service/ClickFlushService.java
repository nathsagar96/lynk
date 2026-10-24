package ly.lynk.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import ly.lynk.config.ClickFlushProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service responsible for safely flushing buffered URL click counts from Redis into PostgreSQL.
 */
@Service
public class ClickFlushService {

    private static final Logger log = LoggerFactory.getLogger(ClickFlushService.class);

    /** Redis key holding click counts currently being processed into the database. */
    public static final String PROCESSING_KEY = "lynk:clicks:processing";

    private final StringRedisTemplate redisTemplate;
    private final JdbcTemplate jdbcTemplate;
    private final ClickFlushProperties properties;

    /**
     * Constructs a click flush service with Redis and JDBC templates.
     *
     * @param redisTemplate template for Redis operations
     * @param jdbcTemplate template for JDBC batch updates
     * @param properties configuration properties for click flushing
     */
    public ClickFlushService(
            StringRedisTemplate redisTemplate, JdbcTemplate jdbcTemplate, ClickFlushProperties properties) {
        this.redisTemplate = redisTemplate;
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
    }

    /**
     * Drains pending click increments and commits them to the database in batches.
     *
     * @return total number of clicks flushed
     */
    @Transactional
    public int flushPendingClicks() {
        int totalFlushed = 0;

        if (Boolean.TRUE.equals(redisTemplate.hasKey(PROCESSING_KEY))) {
            totalFlushed += processKey(PROCESSING_KEY);
        }

        if (Boolean.TRUE.equals(redisTemplate.hasKey(ClickTrackingService.PENDING_CLICKS_KEY))) {
            try {
                redisTemplate.rename(ClickTrackingService.PENDING_CLICKS_KEY, PROCESSING_KEY);
                totalFlushed += processKey(PROCESSING_KEY);
            } catch (RuntimeException e) {
                log.warn("Could not rename pending clicks key: {}", e.getMessage());
            }
        }

        return totalFlushed;
    }

    /**
     * Updates PostgreSQL with clicks in the given key and removes the key upon success.
     *
     * @param key the Redis key holding alias click increments
     * @return the number of clicks processed for this key
     */
    int processKey(String key) {
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(key);
        if (entries.isEmpty()) {
            redisTemplate.delete(key);
            return 0;
        }

        String sql = "UPDATE url_mappings SET click_count = click_count + ? WHERE alias = ?";
        List<Object[]> batchArgs = new ArrayList<>(entries.size());
        int countSum = 0;

        for (Map.Entry<Object, Object> entry : entries.entrySet()) {
            String alias = entry.getKey().toString();
            try {
                long clicks = Long.parseLong(entry.getValue().toString());
                if (clicks > 0) {
                    batchArgs.add(new Object[] {clicks, alias});
                    countSum += clicks;
                }
            } catch (NumberFormatException e) {
                log.warn("Invalid click count for alias={}: {}", alias, entry.getValue());
            }
        }

        if (!batchArgs.isEmpty()) {
            int batchSize = properties.batchSize();
            for (int i = 0; i < batchArgs.size(); i += batchSize) {
                List<Object[]> subList = batchArgs.subList(i, Math.min(i + batchSize, batchArgs.size()));
                jdbcTemplate.batchUpdate(sql, subList);
            }
            log.debug("Flushed {} clicks across {} aliases from key={}", countSum, batchArgs.size(), key);
        }

        redisTemplate.delete(key);
        return countSum;
    }
}
