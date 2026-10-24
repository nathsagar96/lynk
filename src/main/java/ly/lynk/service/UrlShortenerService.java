package ly.lynk.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import ly.lynk.config.AppProperties;
import ly.lynk.dto.ShortenUrlRequest;
import ly.lynk.dto.ShortenUrlResponse;
import ly.lynk.dto.UrlStatsResponse;
import ly.lynk.exception.AliasAlreadyExistsException;
import ly.lynk.exception.AliasExpiredException;
import ly.lynk.exception.AliasGenerationException;
import ly.lynk.exception.AliasNotFoundException;
import ly.lynk.exception.ForbiddenException;
import ly.lynk.exception.InvalidUrlException;
import ly.lynk.exception.ReservedAliasException;
import ly.lynk.model.UrlMapping;
import ly.lynk.repository.UrlMappingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
     * Retrieves details for an existing alias. Masking ownerId for public privacy.
     *
     * @param alias the alias to look up
     * @return response containing the shortened URL details with ownerId masked
     * @throws AliasNotFoundException if the alias does not exist
     */
    @Transactional(readOnly = true)
    public ShortenUrlResponse getUrlDetails(String alias) {
        return toPublicResponse(findRequired(alias));
    }

    /**
     * Retrieves a paginated list of URLs owned by the specified user.
     *
     * @param ownerId the owner subject ID
     * @param pageable pagination details
     * @return page of shortened URL responses
     */
    @Transactional(readOnly = true)
    public Page<ShortenUrlResponse> getUserUrls(String ownerId, Pageable pageable) {
        if (ownerId == null || ownerId.isBlank()) {
            throw new ForbiddenException("Owner ID is required to retrieve user URLs");
        }
        return repository.findByOwnerId(ownerId, pageable).map(this::toResponse);
    }

    /**
     * Retrieves click statistics and metadata for an existing alias, verifying caller ownership.
     *
     * @param alias the alias to query
     * @param currentUserId the calling user's subject identifier
     * @return statistics response containing click counts and metadata
     * @throws AliasNotFoundException if the alias does not exist
     * @throws ForbiddenException if the caller is not the owner
     */
    @Transactional(readOnly = true)
    public UrlStatsResponse getStats(String alias, String currentUserId) {
        UrlMapping mapping = findRequired(alias);
        assertOwnership(mapping, currentUserId);
        return new UrlStatsResponse(
                mapping.getAlias(),
                mapping.getOriginalUrl(),
                buildShortUrl(mapping.getAlias()),
                mapping.getClickCount(),
                mapping.getCreatedAt(),
                mapping.getExpiresAt(),
                mapping.getOwnerId());
    }

    /**
     * Deletes the URL mapping associated with the given alias, verifying caller ownership.
     *
     * @param alias the alias to delete
     * @param currentUserId the calling user's subject identifier
     * @throws AliasNotFoundException if the alias does not exist
     * @throws ForbiddenException if the caller is not the owner
     */
    @Transactional
    public void deleteUrl(String alias, String currentUserId) {
        UrlMapping mapping = findRequired(alias);
        assertOwnership(mapping, currentUserId);
        repository.delete(mapping);
        log.info("Deleted short URL alias={} ownerId={}", alias, mapping.getOwnerId());
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
     * Creates a shortened URL mapping using a custom or auto-generated alias for an unauthenticated request.
     *
     * @param request the shorten request containing original URL and optional alias/expiry
     * @return response with the created mapping details
     */
    public ShortenUrlResponse shortenUrl(ShortenUrlRequest request) {
        return shortenUrl(request, null);
    }

    /**
     * Creates a shortened URL mapping with an owner.
     *
     * @param request the shorten request containing original URL and optional alias/expiry
     * @param ownerId the owner subject ID
     * @return response with the created mapping details
     * @throws ReservedAliasException if the custom alias is reserved
     * @throws AliasAlreadyExistsException if the custom alias already exists
     * @throws InvalidUrlException if the original URL is self-referencing
     * @throws AliasGenerationException if generating a unique alias fails
     */
    public ShortenUrlResponse shortenUrl(ShortenUrlRequest request, String ownerId) {
        String originalUrl = request.originalUrl();
        validateTarget(originalUrl);

        Instant createdAt = clock.instant();
        String customAlias = request.customAlias();

        if (customAlias != null && !customAlias.isBlank()) {
            if (isReserved(customAlias)) {
                throw new ReservedAliasException(customAlias);
            }
            UrlMapping mapping;
            try {
                mapping =
                        writer.save(new UrlMapping(originalUrl, customAlias, createdAt, request.expiresAt(), ownerId));
            } catch (DataIntegrityViolationException e) {
                throw new AliasAlreadyExistsException(customAlias);
            }
            log.info(
                    "Created short URL alias={} originalUrl={} expiresAt={} ownerId={}",
                    mapping.getAlias(),
                    mapping.getOriginalUrl(),
                    mapping.getExpiresAt(),
                    mapping.getOwnerId());
            return toResponse(mapping);
        }

        UrlMapping mapping = generateAndPersist(originalUrl, createdAt, request.expiresAt(), ownerId);
        log.info(
                "Created short URL alias={} originalUrl={} expiresAt={} ownerId={}",
                mapping.getAlias(),
                mapping.getOriginalUrl(),
                mapping.getExpiresAt(),
                mapping.getOwnerId());
        return toResponse(mapping);
    }

    private UrlMapping generateAndPersist(String originalUrl, Instant createdAt, Instant expiresAt, String ownerId) {
        int maxAttempts = properties.maxRetryAttempts();
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            String candidate = aliasGenerator.generate(properties.defaultAliasLength());
            if (isReserved(candidate)) {
                continue;
            }
            try {
                return writer.save(new UrlMapping(originalUrl, candidate, createdAt, expiresAt, ownerId));
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

    private boolean isReserved(String alias) {
        if (alias == null || alias.isBlank()) {
            return false;
        }
        for (String reserved : properties.reservedAliases()) {
            if (reserved.equalsIgnoreCase(alias)) {
                return true;
            }
        }
        return false;
    }

    private void validateTarget(String originalUrl) {
        if (isSelfReferencing(originalUrl)) {
            throw new InvalidUrlException("originalUrl must not point back to its own short URL");
        }
    }

    private boolean isSelfReferencing(String originalUrl) {
        if (originalUrl == null || originalUrl.isBlank()) {
            return false;
        }
        String lower = originalUrl.toLowerCase(Locale.ROOT);
        String base = properties.baseUrl().toLowerCase(Locale.ROOT);
        return lower.equals(base)
                || lower.startsWith(base + "/")
                || lower.startsWith(base + "?")
                || lower.startsWith(base + "#");
    }

    private void assertOwnership(UrlMapping mapping, String currentUserId) {
        if (mapping.getOwnerId() == null || !mapping.getOwnerId().equals(currentUserId)) {
            throw new ForbiddenException("You do not have permission to access or modify this URL");
        }
    }

    private ShortenUrlResponse toResponse(UrlMapping mapping) {
        return new ShortenUrlResponse(
                mapping.getAlias(),
                mapping.getOriginalUrl(),
                buildShortUrl(mapping.getAlias()),
                mapping.getCreatedAt(),
                mapping.getExpiresAt(),
                mapping.getOwnerId());
    }

    private ShortenUrlResponse toPublicResponse(UrlMapping mapping) {
        return new ShortenUrlResponse(
                mapping.getAlias(),
                mapping.getOriginalUrl(),
                buildShortUrl(mapping.getAlias()),
                mapping.getCreatedAt(),
                mapping.getExpiresAt(),
                null);
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
