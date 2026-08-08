package ly.lynk.url;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class UrlCacheServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private UrlCacheService urlCacheService;

    @Test
    void shouldCacheUrlWithCorrectKeyPrefixAndTtl() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        urlCacheService.cacheUrl("abc", "https://example.com", Duration.ofMinutes(10));

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);
        verify(valueOperations).set(keyCaptor.capture(), valueCaptor.capture(), ttlCaptor.capture());

        assertThat(keyCaptor.getValue()).isEqualTo("url:abc");
        assertThat(valueCaptor.getValue()).isEqualTo("https://example.com");
        assertThat(ttlCaptor.getValue()).isEqualTo(Duration.ofMinutes(10));
    }

    @Test
    void shouldReturnCachedUrlWhenPresent() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("url:abc")).thenReturn("https://example.com");

        Optional<String> result = urlCacheService.getCachedUrl("abc");

        assertThat(result).contains("https://example.com");
        verify(valueOperations).get("url:abc");
    }

    @Test
    void shouldReturnEmptyWhenCacheMiss() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("url:missing")).thenReturn(null);

        Optional<String> result = urlCacheService.getCachedUrl("missing");

        assertThat(result).isEmpty();
    }

    @Test
    void shouldEvictCachedUrl() {
        urlCacheService.evict("abc");

        verify(redisTemplate).delete("url:abc");
    }

    @Test
    void shouldNotInteractWithRedisOnEvict() {
        urlCacheService.evict("abc");

        verify(redisTemplate, never()).opsForValue();
    }

    @Test
    void shouldUseCorrectKeyPrefixForAllOperations() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        urlCacheService.cacheUrl("xyz", "https://test.com", Duration.ofSeconds(30));
        urlCacheService.getCachedUrl("xyz");
        urlCacheService.evict("xyz");

        ArgumentCaptor<String> setKeyCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(setKeyCaptor.capture(), anyString(), any(Duration.class));
        assertThat(setKeyCaptor.getValue()).startsWith("url:");

        ArgumentCaptor<String> getKeyCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).get(getKeyCaptor.capture());
        assertThat(getKeyCaptor.getValue()).startsWith("url:");

        verify(redisTemplate).delete(eq("url:xyz"));
    }

    @Test
    void shouldReturnEmptyForDifferentShortcodes() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("url:aaa")).thenReturn(null);
        when(valueOperations.get("url:bbb")).thenReturn("https://other.com");

        Optional<String> resultA = urlCacheService.getCachedUrl("aaa");
        Optional<String> resultB = urlCacheService.getCachedUrl("bbb");

        assertThat(resultA).isEmpty();
        assertThat(resultB).contains("https://other.com");
    }
}
