package ly.lynk.url;

import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ly.lynk.exception.AliasAlreadyExistsException;
import ly.lynk.exception.UrlNotFoundException;
import ly.lynk.exception.UrlOwnershipException;
import ly.lynk.shortcode.Base62Encoder;
import ly.lynk.shortcode.SnowflakeIdGenerator;
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
    private final UrlProperties urlProperties;

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

        Duration expiry = request.expiry() != null ? request.expiry() : urlProperties.defaultExpiry();
        Instant now = Instant.now();
        Instant expiresAt = now.plus(expiry);
        Instant createdAt = now;

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

    @Transactional(readOnly = true)
    public Page<UrlResponse> listUrls(String userId, Pageable pageable) {
        return urlRepository
                .findByUserId(userId, pageable)
                .map(entity -> new UrlResponse(
                        entity.getShortcode(), entity.getOriginalUrl(), entity.getExpiresAt(), entity.getCreatedAt()));
    }

    @Transactional(readOnly = true)
    public UrlResponse getByShortcode(String shortcode, String userId) {
        UrlEntity entity =
                urlRepository.findByShortcode(shortcode).orElseThrow(() -> new UrlNotFoundException(shortcode));
        if (!entity.getUserId().equals(userId)) {
            throw new UrlOwnershipException();
        }
        return new UrlResponse(
                entity.getShortcode(), entity.getOriginalUrl(), entity.getExpiresAt(), entity.getCreatedAt());
    }

    @Transactional
    public void deleteUrl(String shortcode, String userId) {
        UrlEntity entity =
                urlRepository.findByShortcode(shortcode).orElseThrow(() -> new UrlNotFoundException(shortcode));

        if (!entity.getUserId().equals(userId)) {
            throw new UrlOwnershipException();
        }

        urlRepository.delete(entity);
        urlCacheService.evict(shortcode);
        log.info("Deleted short URL: {} by user: {}", shortcode, userId);
    }
}
