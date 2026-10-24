package ly.lynk.service;

import java.time.Clock;
import java.time.Instant;
import ly.lynk.config.AppProperties;
import ly.lynk.dto.ShortenUrlRequest;
import ly.lynk.dto.ShortenUrlResponse;
import ly.lynk.dto.UrlStatsResponse;
import ly.lynk.exception.AliasAlreadyExistsException;
import ly.lynk.exception.AliasExpiredException;
import ly.lynk.exception.AliasGenerationException;
import ly.lynk.exception.AliasNotFoundException;
import ly.lynk.exception.InvalidUrlException;
import ly.lynk.exception.ReservedAliasException;
import ly.lynk.model.UrlMapping;
import ly.lynk.repository.UrlMappingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Core service for creating, resolving, inspecting, and deleting shortened URLs. */
@Service
public class UrlShortenerService {

    private static final Logger log = LoggerFactory.getLogger(UrlShortenerService.class);

    private final UrlMappingRepository repository;
    private final UrlMappingWriter writer;
    private final AliasGenerator aliasGenerator;
    private final AppProperties properties;
    private final Clock clock;

    /**
     * Creates a new URL shortener service with the required dependencies.
     *
     * @param repository the repository for querying mappings and click counters
     * @param writer the transaction-isolated writer for saving mappings
     * @param aliasGenerator generator for Base62 aliases
     * @param properties application configuration properties
     * @param clock clock provider for timestamps
     */
    public UrlShortenerService(
            UrlMappingRepository repository,
            UrlMappingWriter writer,
            AliasGenerator aliasGenerator,
            AppProperties properties,
            Clock clock) {
        this.repository = repository;
        this.writer = writer;
        this.aliasGenerator = aliasGenerator;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Retrieves details for an existing alias.
     *
     * @param alias the alias to look up
     * @return response containing the shortened URL details
     * @throws AliasNotFoundException if the alias does not exist
     */
    @Transactional(readOnly = true)
    public ShortenUrlResponse getUrlDetails(String alias) {
        return toResponse(findRequired(alias));
    }

    /**
     * Retrieves click statistics and metadata for an existing alias.
     *
     * @param alias the alias to query
     * @return statistics response containing click counts and metadata
     * @throws AliasNotFoundException if the alias does not exist
     */
    @Transactional(readOnly = true)
    public UrlStatsResponse getStats(String alias) {
        UrlMapping mapping = findRequired(alias);
        return new UrlStatsResponse(
                mapping.getAlias(),
                mapping.getOriginalUrl(),
                buildShortUrl(mapping.getAlias()),
                mapping.getClickCount(),
                mapping.getCreatedAt(),
                mapping.getExpiresAt());
    }

    /**
     * Deletes the URL mapping associated with the given alias.
     *
     * @param alias the alias to delete
     * @throws AliasNotFoundException if the alias does not exist
     */
    @Transactional
    public void deleteUrl(String alias) {
        UrlMapping mapping = findRequired(alias);
        repository.delete(mapping);
        log.info("Deleted short URL alias={}", alias);
    }

    /**
     * Resolves an alias to its target URL, tracking clicks and appending query parameters.
     *
     * @param alias the alias to resolve
     * @param rawQuery the incoming raw query string, or {@code null}
     * @param trackClick whether to record a click in the database
     * @return the resolved destination URL
     * @throws AliasNotFoundException if the alias does not exist
     * @throws AliasExpiredException if the alias has expired
     */
    @Transactional
    public String resolveAndTrack(String alias, String rawQuery, boolean trackClick) {
        UrlMapping mapping = findRequired(alias);
        if (isExpired(mapping)) {
            throw new AliasExpiredException(alias);
        }
        if (trackClick) {
            repository.incrementClickCount(alias);
        }
        String target = mergeQuery(mapping.getOriginalUrl(), rawQuery);
        log.debug("Resolved alias={} trackClick={} target={}", alias, trackClick, target);
        return target;
    }

    /**
     * Creates a shortened URL mapping using a custom or auto-generated alias.
     *
     * @param request the shorten request containing original URL and optional alias/expiry
     * @return response with the created mapping details
     * @throws ReservedAliasException if the custom alias is reserved
     * @throws AliasAlreadyExistsException if the custom alias already exists
     * @throws InvalidUrlException if the original URL is self-referencing
     * @throws AliasGenerationException if generating a unique alias fails
     */
    public ShortenUrlResponse shortenUrl(ShortenUrlRequest request) {
        String originalUrl = request.originalUrl();
        validateTarget(originalUrl, request.customAlias());

        Instant createdAt = clock.instant();
        String customAlias = request.customAlias();

        if (customAlias != null && !customAlias.isBlank()) {
            if (properties.reservedAliases().contains(customAlias)) {
                throw new ReservedAliasException(customAlias);
            }
            UrlMapping mapping;
            try {
                mapping = writer.save(new UrlMapping(originalUrl, customAlias, createdAt, request.expiresAt()));
            } catch (DataIntegrityViolationException e) {
                throw new AliasAlreadyExistsException(customAlias);
            }
            log.info(
                    "Created short URL alias={} originalUrl={} expiresAt={}",
                    mapping.getAlias(),
                    mapping.getOriginalUrl(),
                    mapping.getExpiresAt());
            return toResponse(mapping);
        }

        UrlMapping mapping = generateAndPersist(originalUrl, createdAt, request.expiresAt());
        log.info(
                "Created short URL alias={} originalUrl={} expiresAt={}",
                mapping.getAlias(),
                mapping.getOriginalUrl(),
                mapping.getExpiresAt());
        return toResponse(mapping);
    }

    private UrlMapping generateAndPersist(String originalUrl, Instant createdAt, Instant expiresAt) {
        int maxAttempts = properties.maxRetryAttempts();
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            String candidate = aliasGenerator.generate(properties.defaultAliasLength());
            if (isSelfReferencing(originalUrl, candidate)) {
                continue;
            }
            try {
                return writer.save(new UrlMapping(originalUrl, candidate, createdAt, expiresAt));
            } catch (DataIntegrityViolationException e) {
                log.warn(
                        "Alias collision on candidate {}, retrying (attempt {}/{})",
                        candidate,
                        attempt + 1,
                        maxAttempts);
            }
        }
        throw new AliasGenerationException("Unable to generate a unique alias after " + maxAttempts + " attempts");
    }

    private UrlMapping findRequired(String alias) {
        return repository.findByAlias(alias).orElseThrow(() -> new AliasNotFoundException(alias));
    }

    private boolean isExpired(UrlMapping mapping) {
        Instant expiresAt = mapping.getExpiresAt();
        return expiresAt != null && !clock.instant().isBefore(expiresAt);
    }

    private void validateTarget(String originalUrl, String customAlias) {
        if (isSelfReferencing(originalUrl, customAlias)) {
            throw new InvalidUrlException("originalUrl must not point back to its own short URL");
        }
    }

    private boolean isSelfReferencing(String originalUrl, String alias) {
        if (alias == null || alias.isBlank()) {
            return false;
        }
        String lower = originalUrl.toLowerCase();
        String shortUrl = buildShortUrl(alias).toLowerCase();
        return lower.equals(shortUrl)
                || lower.startsWith(shortUrl + "?")
                || lower.startsWith(shortUrl + "/")
                || lower.startsWith(shortUrl + "#");
    }

    private ShortenUrlResponse toResponse(UrlMapping mapping) {
        return new ShortenUrlResponse(
                mapping.getAlias(),
                mapping.getOriginalUrl(),
                buildShortUrl(mapping.getAlias()),
                mapping.getCreatedAt(),
                mapping.getExpiresAt());
    }

    /** Builds the public short URL from the configured base URL. */
    private String buildShortUrl(String alias) {
        return properties.baseUrl() + "/" + alias;
    }

    /**
     * Appends the incoming raw query string to the target URL, preserving encoding.
     *
     * <p>The raw query is never reparsed to avoid failing redirects on malformed escapes.
     */
    private String mergeQuery(String targetUrl, String rawQuery) {
        if (rawQuery == null || rawQuery.isBlank()) {
            return targetUrl;
        }
        int fragment = targetUrl.indexOf('#');
        String base = fragment == -1 ? targetUrl : targetUrl.substring(0, fragment);
        String tail = fragment == -1 ? "" : targetUrl.substring(fragment);
        String separator = "?";
        if (base.contains("?")) {
            separator = base.endsWith("?") || base.endsWith("&") ? "" : "&";
        }
        return base + separator + rawQuery + tail;
    }
}
