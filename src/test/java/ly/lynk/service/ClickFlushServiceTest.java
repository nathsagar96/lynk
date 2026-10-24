package ly.lynk.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Map;
import ly.lynk.config.ClickFlushProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

class ClickFlushServiceTest {

    private StringRedisTemplate redisTemplate;
    private HashOperations<String, Object, Object> hashOperations;
    private JdbcTemplate jdbcTemplate;
    private ClickFlushService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        hashOperations = mock(HashOperations.class);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        jdbcTemplate = mock(JdbcTemplate.class);
        ClickFlushProperties properties = new ClickFlushProperties(Duration.ofSeconds(5), 100);
        service = new ClickFlushService(redisTemplate, jdbcTemplate, properties);
    }

    @Test
    @DisplayName("returns zero when no pending clicks exist")
    void returnsZeroWhenNoPendingClicks() {
        when(redisTemplate.hasKey(ClickFlushService.PROCESSING_KEY)).thenReturn(false);
        when(redisTemplate.hasKey(ClickTrackingService.PENDING_CLICKS_KEY)).thenReturn(false);

        int flushed = service.flushPendingClicks();

        assertThat(flushed).isZero();
        verify(jdbcTemplate, never()).batchUpdate(anyString(), anyList());
    }

    @Test
    @DisplayName("flushes pending clicks to database in batch and deletes processing key")
    void flushesPendingClicksToDatabase() {
        when(redisTemplate.hasKey(ClickFlushService.PROCESSING_KEY)).thenReturn(false);
        when(redisTemplate.hasKey(ClickTrackingService.PENDING_CLICKS_KEY)).thenReturn(true);
        when(hashOperations.entries(ClickFlushService.PROCESSING_KEY))
                .thenReturn(Map.of("alias1", "10", "alias2", "5"));

        int flushed = service.flushPendingClicks();

        assertThat(flushed).isEqualTo(15);
        verify(redisTemplate).rename(ClickTrackingService.PENDING_CLICKS_KEY, ClickFlushService.PROCESSING_KEY);
        verify(jdbcTemplate)
                .batchUpdate(eq("UPDATE url_mappings SET click_count = click_count + ? WHERE alias = ?"), anyList());
        verify(redisTemplate).delete(ClickFlushService.PROCESSING_KEY);
    }

    @Test
    @DisplayName("flushes leftover processing key from previous crashed run")
    void flushesLeftoverProcessingKey() {
        when(redisTemplate.hasKey(ClickFlushService.PROCESSING_KEY)).thenReturn(true);
        when(redisTemplate.hasKey(ClickTrackingService.PENDING_CLICKS_KEY)).thenReturn(false);
        when(hashOperations.entries(ClickFlushService.PROCESSING_KEY)).thenReturn(Map.of("recovered", "42"));

        int flushed = service.flushPendingClicks();

        assertThat(flushed).isEqualTo(42);
        verify(jdbcTemplate)
                .batchUpdate(eq("UPDATE url_mappings SET click_count = click_count + ? WHERE alias = ?"), anyList());
        verify(redisTemplate).delete(ClickFlushService.PROCESSING_KEY);
    }

    @Test
    @DisplayName("cleans up empty processing key without running database update")
    void cleansUpEmptyProcessingKey() {
        when(redisTemplate.hasKey(ClickFlushService.PROCESSING_KEY)).thenReturn(true);
        when(redisTemplate.hasKey(ClickTrackingService.PENDING_CLICKS_KEY)).thenReturn(false);
        when(hashOperations.entries(ClickFlushService.PROCESSING_KEY)).thenReturn(Map.of());

        int flushed = service.flushPendingClicks();

        assertThat(flushed).isZero();
        verify(jdbcTemplate, never()).batchUpdate(anyString(), anyList());
        verify(redisTemplate).delete(ClickFlushService.PROCESSING_KEY);
    }
}
