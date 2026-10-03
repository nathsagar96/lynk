package com.lynk.service;

import com.lynk.config.UrlShortenerProperties;
import com.lynk.domain.UrlMapping;
import com.lynk.dto.request.ShortenUrlRequest;
import com.lynk.dto.response.ShortenUrlResponse;
import com.lynk.dto.response.UrlStatsResponse;
import com.lynk.error.LinkException;
import com.lynk.repository.UrlMappingRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
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
     * Registers a long URL and returns the shortened form.
     * <p>
     * When a custom alias is given it is used verbatim and a collision is a 409. Otherwise codes
     * are generated at random and a collision is retried, since two random codes clashing is not
     * the caller's problem.
     */
    @Transactional
    public ShortenUrlResponse shorten(ShortenUrlRequest request) {
        String originalUrl = validator.validateUrl(request.url());
        Instant expiresAt = (request.hoursToExpire() == null)
                ? null
                : Instant.now().plus(request.hoursToExpire(), ChronoUnit.HOURS);

        UrlMapping mapping =
                (request.customAlias() != null && !request.customAlias().isBlank())
                        ? saveWithAlias(validator.validateAlias(request.customAlias()), originalUrl, expiresAt)
                        : saveWithGeneratedCode(originalUrl, expiresAt);

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
     */
    @Transactional(readOnly = true)
    public UrlStatsResponse stats(String shortCode) {
        UrlMapping mapping = repository.findByShortCode(shortCode).orElseThrow(() -> LinkException.notFound(shortCode));
        return new UrlStatsResponse(
                mapping.getOriginalUrl(),
                buildShortUrl(mapping.getShortCode()),
                mapping.getCreatedAt(),
                mapping.getExpiresAt(),
                mapping.getClickCount());
    }

    private UrlMapping saveWithAlias(String alias, String originalUrl, Instant expiresAt) {
        // Pre-check gives a clean 409 for the ordinary case; the unique constraint below is what
        // actually makes it correct when two requests race for the same alias.
        if (repository.existsByShortCode(alias)) {
            throw LinkException.aliasConflict(alias);
        }
        try {
            // Flush here so the unique-constraint violation surfaces inside the retry logic.
            return repository.saveAndFlush(new UrlMapping(originalUrl, alias, expiresAt));
        } catch (DataIntegrityViolationException ex) {
            throw LinkException.aliasConflict(alias);
        }
    }

    private UrlMapping saveWithGeneratedCode(String originalUrl, Instant expiresAt) {
        int attempts = properties.code().maxAttempts();
        for (int attempt = 1; attempt <= attempts; attempt++) {
            String code = codeGenerator.generate();
            try {
                return repository.saveAndFlush(new UrlMapping(originalUrl, code, expiresAt));
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
