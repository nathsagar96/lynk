package ly.lynk.url;

import java.time.Duration;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UrlCacheService {

    private static final String KEY_PREFIX = "url:";

    private final StringRedisTemplate redisTemplate;

    public void cacheUrl(String shortcode, String originalUrl, Duration ttl) {
        redisTemplate.opsForValue().set(KEY_PREFIX + shortcode, originalUrl, ttl);
    }

    public Optional<String> getCachedUrl(String shortcode) {
        String value = redisTemplate.opsForValue().get(KEY_PREFIX + shortcode);
        return Optional.ofNullable(value);
    }

    public void evict(String shortcode) {
        redisTemplate.delete(KEY_PREFIX + shortcode);
    }
}
