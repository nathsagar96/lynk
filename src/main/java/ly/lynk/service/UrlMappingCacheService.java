package ly.lynk.service;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import ly.lynk.config.CacheConfig;
import ly.lynk.dto.CachedUrlMapping;
import ly.lynk.exception.AliasNotFoundException;
import ly.lynk.repository.UrlMappingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Two-tier cache access and eviction service for URL alias mappings (L1 Caffeine in-memory, L2 Redis).
 */
@Service
public class UrlMappingCacheService {

    private static final Logger log = LoggerFactory.getLogger(UrlMappingCacheService.class);

    private final UrlMappingRepository repository;
    private final CacheManager cacheManager;
    private final com.github.benmanes.caffeine.cache.Cache<String, CachedUrlMapping> l1Cache;

    /**
     * Creates a new cache service backed by repository, cache manager, and default Caffeine L1 cache.
     *
     * @param repository the repository for querying database mappings
     * @param cacheManager the Spring cache manager for distributed L2 Redis cache
     */
    @Autowired
    public UrlMappingCacheService(UrlMappingRepository repository, CacheManager cacheManager) {
        this(
                repository,
                cacheManager,
                Caffeine.newBuilder()
                        .maximumSize(50_000)
                        .expireAfterWrite(Duration.ofSeconds(60))
                        .build());
    }

    UrlMappingCacheService(
            UrlMappingRepository repository,
            CacheManager cacheManager,
            com.github.benmanes.caffeine.cache.Cache<String, CachedUrlMapping> l1Cache) {
        this.repository = repository;
        this.cacheManager = cacheManager;
        this.l1Cache = l1Cache;
    }

    /**
     * Resolves a cached URL mapping by alias checking L1 Caffeine first, then L2 Redis,
     * and falling back to database on cache miss.
     *
     * @param alias the alias to look up
     * @return the cached mapping details
     * @throws AliasNotFoundException if the alias is not found in cache or database
     */
    @Transactional(readOnly = true)
    public CachedUrlMapping get(String alias) {
        CachedUrlMapping l1Hit = l1Cache.getIfPresent(alias);
        if (l1Hit != null) {
            return l1Hit;
        }

        Cache cache = getRedisCache();
        if (cache != null) {
            try {
                Cache.ValueWrapper wrapper = cache.get(alias);
                if (wrapper != null && wrapper.get() instanceof CachedUrlMapping mapping) {
                    l1Cache.put(alias, mapping);
                    return mapping;
                }
            } catch (RuntimeException e) {
                log.warn("L2 Cache GET failed for alias={}: {}", alias, e.getMessage());
            }
        }

        CachedUrlMapping mapping = repository
                .findByAlias(alias)
                .map(CachedUrlMapping::from)
                .orElseThrow(() -> new AliasNotFoundException(alias));

        l1Cache.put(alias, mapping);
        if (cache != null) {
            try {
                cache.put(alias, mapping);
            } catch (RuntimeException e) {
                log.warn("L2 Cache PUT failed for alias={}: {}", alias, e.getMessage());
            }
        }
        return mapping;
    }

    /**
     * Populates both L1 and L2 caches with the specified mapping (e.g. for write-through pre-warming).
     *
     * @param mapping the mapping to cache
     */
    public void put(CachedUrlMapping mapping) {
        l1Cache.put(mapping.alias(), mapping);
        Cache cache = getRedisCache();
        if (cache != null) {
            try {
                cache.put(mapping.alias(), mapping);
            } catch (RuntimeException e) {
                log.warn("L2 Cache PUT failed for alias={}: {}", mapping.alias(), e.getMessage());
            }
        }
    }

    /**
     * Immediately evicts the specified alias from both L1 and L2 caches.
     *
     * @param alias the alias to evict
     */
    private void evict(String alias) {
        l1Cache.invalidate(alias);
        try {
            Cache cache = getRedisCache();
            if (cache != null) {
                cache.evict(alias);
            }
        } catch (RuntimeException e) {
            log.warn(
                    "Cache EVICT failed for alias={} in cache={}: {}",
                    alias,
                    CacheConfig.URL_MAPPINGS_CACHE,
                    e.getMessage());
        }
    }

    /**
     * Evicts the specified alias from cache after the surrounding transaction commits.
     * If no transaction is active, evicts immediately.
     *
     * @param alias the alias to evict
     */
    public void evictAfterCommit(String alias) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    evict(alias);
                }
            });
        } else {
            evict(alias);
        }
    }

    private Cache getRedisCache() {
        try {
            return cacheManager.getCache(CacheConfig.URL_MAPPINGS_CACHE);
        } catch (RuntimeException e) {
            log.warn("Could not obtain Redis cache: {}", e.getMessage());
            return null;
        }
    }
}
