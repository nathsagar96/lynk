package com.lynk.service;

import com.lynk.config.UrlShortenerProperties;
import com.lynk.domain.UrlMapping;
import com.lynk.dto.request.ShortenUrlRequest;
import com.lynk.dto.response.LinkPageResponse;
import com.lynk.dto.response.ShortenUrlResponse;
import com.lynk.dto.response.UrlStatsResponse;
import com.lynk.error.LinkException;
import com.lynk.repository.UrlMappingRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Core shortener behaviour: create mappings, resolve them, and count clicks.
 */
@Service
@RequiredArgsConstructor
public class UrlShortenerService {

    private final UrlMappingRepository repository;

    private final ShortCodeGenerator codeGenerator;

    private final UrlValidator validator;

    private final UrlShortenerProperties properties;

    /**
     * Registers a long URL and returns its shortened form, owned by {@code owner}.
     * <p>
     * When a custom alias is given it is used verbatim and a collision is a 409. Otherwise codes
     * are generated at random and a collision is retried, since two random codes clashing is not
     * the caller's problem. Aliases stay globally unique, so a second owner asking for a code that is
     * already taken gets that same 409.
     *
     * @param owner token subject the link is attributed to; must be non-blank, since an
     *                     unowned link could never have its stats read back
     */
    @Transactional
    public ShortenUrlResponse shorten(ShortenUrlRequest request, String owner) {
        String originalUrl = validator.validateUrl(request.url());
        Instant expiresAt = (request.hoursToExpire() == null)
                ? null
                : Instant.now().plus(request.hoursToExpire(), ChronoUnit.HOURS);

        UrlMapping mapping =
                (request.customAlias() != null && !request.customAlias().isBlank())
                        ? saveWithAlias(validator.validateAlias(request.customAlias()), originalUrl, owner, expiresAt)
                        : saveWithGeneratedCode(originalUrl, owner, expiresAt);

        return new ShortenUrlResponse(
                buildShortUrl(mapping.getShortCode()),
                mapping.getShortCode(),
                mapping.getOriginalUrl(),
                mapping.getExpiresAt());
    }

    /**
     * Resolves a short code to its destination, rejecting expired links and counting the click.
     * <p>
     * The read and the atomic increment happen in one transaction, so a link that expires between
     * the two cannot record a click against itself.
     */
    @Transactional
    public String resolveAndCountClick(String shortCode) {
        UrlMapping mapping = repository.findByShortCode(shortCode).orElseThrow(() -> LinkException.notFound(shortCode));

        if (mapping.isExpired(Instant.now())) {
            throw LinkException.expired(shortCode, mapping.getExpiresAt());
        }

        repository.incrementClickCount(shortCode);
        return mapping.getOriginalUrl();
    }

    /**
     * Returns click statistics for a short code, including for links that have since expired.
     * <p>
     * Scoped to the owner: another subject's link answers the same 404 as a code that never existed,
     * rather than a 403, because a 403 would confirm the code is real.
     */
    @Transactional(readOnly = true)
    public UrlStatsResponse stats(String shortCode, String owner) {
        UrlMapping mapping = repository
                .findByShortCodeAndOwner(shortCode, owner)
                .orElseThrow(() -> LinkException.notFound(shortCode));
        return toResponse(mapping);
    }

    /**
     * Returns one page of the links {@code owner} registered, newest first.
     * <p>
     * Scoped by owner in the query rather than filtered after the fact, so a page of somebody else's
     * links cannot leak through a gap in the count. Expired links are included: they are still
     * recorded until the nightly sweep removes them.
     */
    @Transactional(readOnly = true)
    public LinkPageResponse list(String owner, int page, int size) {
        Page<UrlMapping> found = repository.findByOwnerOrderByCreatedAtDescIdDesc(owner, PageRequest.of(page, size));
        return new LinkPageResponse(
                found.getContent().stream().map(this::toResponse).toList(), found.getTotalElements());
    }

    /**
     * Removes one of {@code owner}'s links, so its short code stops resolving at all.
     * <p>
     * Scoped like {@link #stats}: a link belonging to somebody else is reported as missing rather
     * than forbidden, so this cannot be used to probe which codes exist.
     */
    @Transactional
    public void delete(String shortCode, String owner) {
        UrlMapping mapping = repository
                .findByShortCodeAndOwner(shortCode, owner)
                .orElseThrow(() -> LinkException.notFound(shortCode));
        repository.delete(mapping);
    }

    /** The single representation of a link, shared by the stats endpoint and the listing. */
    private UrlStatsResponse toResponse(UrlMapping mapping) {
        return new UrlStatsResponse(
                mapping.getOriginalUrl(),
                buildShortUrl(mapping.getShortCode()),
                mapping.getCreatedAt(),
                mapping.getExpiresAt(),
                mapping.getClickCount());
    }

    private UrlMapping saveWithAlias(String alias, String originalUrl, String owner, Instant expiresAt) {
        // Pre-check gives a clean 409 for the ordinary case; the unique constraint below is what
        // actually makes it correct when two requests race for the same alias.
        if (repository.existsByShortCode(alias)) {
            throw LinkException.aliasConflict(alias);
        }
        try {
            // Flush here so the unique-constraint violation surfaces inside the retry logic.
            return repository.saveAndFlush(new UrlMapping(originalUrl, alias, owner, expiresAt));
        } catch (DataIntegrityViolationException ex) {
            throw LinkException.aliasConflict(alias);
        }
    }

    private UrlMapping saveWithGeneratedCode(String originalUrl, String owner, Instant expiresAt) {
        int attempts = properties.code().maxAttempts();
        for (int attempt = 1; attempt <= attempts; attempt++) {
            String code = codeGenerator.generate();
            try {
                return repository.saveAndFlush(new UrlMapping(originalUrl, code, owner, expiresAt));
            } catch (DataIntegrityViolationException ex) {
                if (attempt == attempts) {
                    throw ex;
                }
            }
        }
        throw new IllegalStateException("unreachable: retry loop always returns or rethrows");
    }

    private String buildShortUrl(String shortCode) {
        String base = properties.baseUrl();
        return base.endsWith("/") ? base + shortCode : base + "/" + shortCode;
    }
}
