package ly.lynk.url;

import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ly.lynk.click.ClickEvent;
import ly.lynk.common.LynkProperties;
import ly.lynk.common.exception.AliasAlreadyExistsException;
import ly.lynk.common.exception.UrlExpiredException;
import ly.lynk.common.exception.UrlNotFoundException;
import ly.lynk.common.exception.UrlOwnershipException;
import ly.lynk.shortcode.Base62Encoder;
import ly.lynk.shortcode.SnowflakeIdGenerator;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class UrlService {

    private final UrlRepository urlRepository;
    private final UrlCacheService urlCacheService;
    private final SnowflakeIdGenerator snowflakeIdGenerator;
    private final LynkProperties lynkProperties;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public UrlResponse createUrl(CreateUrlRequest request, String userId) {
        long id = snowflakeIdGenerator.nextId();

        String shortcode;
        if (request.alias() != null && !request.alias().isBlank()) {
            if (urlRepository.existsByShortcode(request.alias())) {
                throw new AliasAlreadyExistsException(request.alias());
            }
            shortcode = request.alias();
        } else {
            shortcode = Base62Encoder.encode(id);
        }

        Duration expiry = request.expiry() != null
                ? request.expiry()
                : lynkProperties.url().defaultExpiry();
        Instant expiresAt = Instant.now().plus(expiry);
        Instant createdAt = Instant.now();

        var entity = UrlEntity.builder()
                .id(id)
                .shortcode(shortcode)
                .originalUrl(request.url())
                .userId(userId)
                .expiresAt(expiresAt)
                .createdAt(createdAt)
                .build();

        urlRepository.save(entity);
        urlCacheService.cacheUrl(shortcode, request.url(), expiry);

        log.info("Created short URL: {} -> {} for user: {}", shortcode, request.url(), userId);
        return new UrlResponse(shortcode, request.url(), expiresAt, createdAt);
    }

    public String resolveAndTrack(String shortcode, String ipAddress, String userAgent, String referer) {
        String originalUrl = urlCacheService.getCachedUrl(shortcode).orElseGet(() -> resolveFromDb(shortcode));

        eventPublisher.publishEvent(new ClickEvent(shortcode, ipAddress, userAgent, referer, Instant.now()));

        return originalUrl;
    }

    @Transactional(readOnly = true)
    public Page<UrlResponse> listUrls(String userId, Pageable pageable) {
        return urlRepository
                .findByUserId(userId, pageable)
                .map(entity -> new UrlResponse(
                        entity.getShortcode(), entity.getOriginalUrl(), entity.getExpiresAt(), entity.getCreatedAt()));
    }

    @Transactional
    public void deleteUrl(String shortcode, String userId) {
        UrlEntity entity =
                urlRepository.findByShortcode(shortcode).orElseThrow(() -> new UrlNotFoundException(shortcode));

        if (!entity.getUserId().equals(userId)) {
            throw new UrlOwnershipException(shortcode, userId);
        }

        urlRepository.delete(entity);
        urlCacheService.evict(shortcode);
        log.info("Deleted short URL: {} by user: {}", shortcode, userId);
    }

    private String resolveFromDb(String shortcode) {
        UrlEntity entity =
                urlRepository.findByShortcode(shortcode).orElseThrow(() -> new UrlNotFoundException(shortcode));

        if (entity.getExpiresAt().isBefore(Instant.now())) {
            throw new UrlExpiredException(shortcode);
        }

        Duration remainingTtl = Duration.between(Instant.now(), entity.getExpiresAt());
        urlCacheService.cacheUrl(shortcode, entity.getOriginalUrl(), remainingTtl);

        return entity.getOriginalUrl();
    }
}
