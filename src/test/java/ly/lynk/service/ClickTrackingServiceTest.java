package ly.lynk.service;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

class ClickTrackingServiceTest {

    private StringRedisTemplate redisTemplate;
    private HashOperations<String, Object, Object> hashOperations;
    private ClickTrackingService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        hashOperations = mock(HashOperations.class);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        service = new ClickTrackingService(redisTemplate);
    }

    @Test
    @DisplayName("increments click count in Redis pending hash")
    void incrementsClickCountInRedis() {
        service.recordClick("test-alias");

        verify(hashOperations).increment(ClickTrackingService.PENDING_CLICKS_KEY, "test-alias", 1L);
    }

    @Test
    @DisplayName("fails open when Redis operation throws an exception")
    void failsOpenOnRedisException() {
        doThrow(new RedisConnectionFailureException("Redis is down"))
                .when(hashOperations)
                .increment(eq(ClickTrackingService.PENDING_CLICKS_KEY), eq("test-alias"), anyLong());

        // Must not throw exception
        service.recordClick("test-alias");
    }
}
