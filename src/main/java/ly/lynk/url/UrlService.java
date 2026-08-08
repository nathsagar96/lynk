package ly.lynk.url;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ly.lynk.exception.AliasAlreadyExistsException;
import ly.lynk.exception.UrlNotFoundException;
import ly.lynk.exception.UrlOwnershipException;
import ly.lynk.shortcode.Base62Encoder;
import ly.lynk.shortcode.SnowflakeIdGenerator;
import org.springframework.dao.DataIntegrityViolationException;
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
    private final UrlMapper urlMapper;

    @Transactional
    public UrlResponse createUrl(CreateUrlRequest request, String userId) {
        long id = snowflakeIdGenerator.nextId();

        boolean hasCustomAlias = request.alias() != null && !request.alias().isBlank();
        String shortcode = hasCustomAlias ? request.alias() : Base62Encoder.encode(id);

        Duration expiry = request.expiry() != null ? request.expiry() : urlProperties.defaultExpiry();
        Instant now = Instant.now();
        Instant expiresAt = now.plus(expiry);

        var entity = UrlEntity.builder()
                .id(id)
                .shortcode(shortcode)
                .originalUrl(request.url())
                .userId(userId)
                .expiresAt(expiresAt)
                .createdAt(now)
                .build();

        try {
            urlRepository.save(entity);
        } catch (DataIntegrityViolationException ex) {
            if (hasCustomAlias) {
                throw new AliasAlreadyExistsException(request.alias());
            }
            throw ex;
        }

        urlCacheService.cacheUrl(shortcode, request.url(), expiry);

        log.info("Created short URL: {} -> {} for user: {}", shortcode, sanitizeUrlForLog(request.url()), userId);
        return urlMapper.toResponse(shortcode, request.url(), expiresAt, now);
    }

    private String sanitizeUrlForLog(String url) {
        try {
            URI uri = URI.create(url);
            return uri.getScheme() + "://" + uri.getHost();
        } catch (Exception e) {
            return "<invalid-url>";
        }
    }

    @Transactional(readOnly = true)
    public Page<UrlResponse> listUrls(String userId, Pageable pageable) {
        return urlRepository.findByUserId(userId, pageable).map(urlMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public UrlResponse getByShortcode(String shortcode, String userId) {
        UrlEntity entity =
                urlRepository.findByShortcode(shortcode).orElseThrow(() -> new UrlNotFoundException(shortcode));
        if (!entity.getUserId().equals(userId)) {
            throw new UrlOwnershipException();
        }
        return urlMapper.toResponse(entity);
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
