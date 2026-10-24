package ly.lynk.service;

import ly.lynk.config.CacheConfig;
import ly.lynk.dto.CachedUrlMapping;
import ly.lynk.exception.AliasNotFoundException;
import ly.lynk.repository.UrlMappingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Cache access and eviction service for URL alias mappings.
 *
 * <p>Separating the cache proxy into this service ensures that Spring AOP caching interceptors
 * are invoked properly without internal self-invocation bypass.
 */
@Service
public class UrlMappingCacheService {

    private static final Logger log = LoggerFactory.getLogger(UrlMappingCacheService.class);

    private final UrlMappingRepository repository;
    private final CacheManager cacheManager;

    /**
     * Creates a new cache service backed by the URL mapping repository and cache manager.
     *
     * @param repository the repository for querying database mappings
     * @param cacheManager the Spring cache manager for explicit cache evictions
     */
    public UrlMappingCacheService(UrlMappingRepository repository, CacheManager cacheManager) {
        this.repository = repository;
        this.cacheManager = cacheManager;
    }

    /**
     * Resolves a cached URL mapping by alias, fetching from the database on a cache miss.
     *
     * @param alias the alias to look up
     * @return the cached mapping details
     * @throws AliasNotFoundException if the alias is not found in cache or database
     */
    @Transactional(readOnly = true)
    @Cacheable(value = CacheConfig.URL_MAPPINGS_CACHE, key = "#alias")
    public CachedUrlMapping get(String alias) {
        return repository
                .findByAlias(alias)
                .map(CachedUrlMapping::from)
                .orElseThrow(() -> new AliasNotFoundException(alias));
    }

    /**
     * Immediately evicts the specified alias from the Redis cache.
     *
     * <p>Catches and logs any cache exception to maintain fail-open resilience during Redis outages.
     *
     * @param alias the alias to evict
     */
    private void evict(String alias) {
        try {
            Cache cache = cacheManager.getCache(CacheConfig.URL_MAPPINGS_CACHE);
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
     * Evicts the specified alias from the cache after the surrounding transaction commits.
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
}
