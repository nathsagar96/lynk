package ly.lynk.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import ly.lynk.TestSecurityConfiguration;
import ly.lynk.TestcontainersConfiguration;
import ly.lynk.config.CacheConfig;
import ly.lynk.dto.CachedUrlMapping;
import ly.lynk.dto.ShortenUrlRequest;
import ly.lynk.model.UrlMapping;
import ly.lynk.repository.UrlMappingRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import({TestcontainersConfiguration.class, TestSecurityConfiguration.class})
class UrlMappingCacheIT {

    @Autowired
    private UrlShortenerService shortenerService;

    @Autowired
    private UrlMappingRepository repository;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private CacheConfig cacheConfig;

    @Autowired
    private ClickFlushService clickFlushService;

    @Test
    @DisplayName("resolving a URL caches mapping in Redis and serves subsequent resolutions from cache")
    void cachesUrlMappingAndTracksClicks() {
        String ownerId = UUID.randomUUID().toString();
        String alias = "cache-" + UUID.randomUUID().toString().substring(0, 8);
        String originalUrl = "https://example.com/target";

        shortenerService.shortenUrl(new ShortenUrlRequest(originalUrl, alias, null), ownerId);

        Cache cache = cacheManager.getCache(CacheConfig.URL_MAPPINGS_CACHE);
        assertThat(cache).isNotNull();

        // 1. Initial state: pre-warmed cache means entry is populated immediately upon creation
        Cache.ValueWrapper prewarmed = cache.get(alias);
        assertThat(prewarmed).isNotNull();
        assertThat(prewarmed.get()).isInstanceOf(CachedUrlMapping.class);

        // 2. First resolution: populates cache and buffers click count
        String resolved1 = shortenerService.resolveAndTrack(alias, null, true);
        assertThat(resolved1).isEqualTo(originalUrl);

        // Flush buffered clicks from Redis to DB
        clickFlushService.flushPendingClicks();

        Cache.ValueWrapper cachedWrapper = cache.get(alias);
        assertThat(cachedWrapper).isNotNull();
        assertThat(cachedWrapper.get()).isInstanceOf(CachedUrlMapping.class);
        CachedUrlMapping cached = (CachedUrlMapping) cachedWrapper.get();
        assertThat(cached.alias()).isEqualTo(alias);
        assertThat(cached.originalUrl()).isEqualTo(originalUrl);

        UrlMapping inDb = repository.findByAlias(alias).orElseThrow();
        assertThat(inDb.getClickCount()).isEqualTo(1L);

        // 3. Delete directly from DB (bypassing service eviction) to prove subsequent read is served from Redis
        repository.delete(inDb);
        assertThat(repository.findByAlias(alias)).isEmpty();

        // 4. Second resolution: served directly from Redis cache despite entity being absent in DB
        String resolved2 = shortenerService.resolveAndTrack(alias, null, false);
        assertThat(resolved2).isEqualTo(originalUrl);

        // 5. Eviction removes entry from Redis
        cache.evict(alias);
        assertThat(cache.get(alias)).isNull();
    }

    @Test
    @DisplayName("CacheErrorHandler suppresses cache exceptions enabling fail-open database fallback")
    void cacheErrorHandlerSuppressesErrors() {
        CacheErrorHandler errorHandler = cacheConfig.errorHandler();
        Cache cache = cacheManager.getCache(CacheConfig.URL_MAPPINGS_CACHE);
        RuntimeException error = new RuntimeException("Simulated Redis network partition");

        // Fail-open: methods must not throw exceptions
        errorHandler.handleCacheGetError(error, cache, "key");
        errorHandler.handleCachePutError(error, cache, "key", "val");
        errorHandler.handleCacheEvictError(error, cache, "key");
        errorHandler.handleCacheClearError(error, cache);
    }

    @Test
    @DisplayName("evictAfterCommit suppresses exceptions when cache eviction fails")
    void evictAfterCommitSuppressesExceptionsOnFailure() {
        CacheManager failingCacheManager = Mockito.mock(CacheManager.class);
        Cache failingCache = Mockito.mock(Cache.class);
        Mockito.when(failingCacheManager.getCache(CacheConfig.URL_MAPPINGS_CACHE))
                .thenReturn(failingCache);
        Mockito.doThrow(new RuntimeException("Simulated Redis eviction timeout"))
                .when(failingCache)
                .evict("failing-alias");

        UrlMappingCacheService cacheService = new UrlMappingCacheService(repository, failingCacheManager);

        // Fail-open: must not throw exception
        cacheService.evictAfterCommit("failing-alias");
    }
}
