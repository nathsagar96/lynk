package ly.lynk.url;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import ly.lynk.click.ClickEvent;
import ly.lynk.common.LynkProperties;
import ly.lynk.common.exception.AliasAlreadyExistsException;
import ly.lynk.common.exception.UrlExpiredException;
import ly.lynk.common.exception.UrlNotFoundException;
import ly.lynk.common.exception.UrlOwnershipException;
import ly.lynk.shortcode.SnowflakeIdGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class UrlServiceTest {

    @Mock
    private UrlRepository urlRepository;

    @Mock
    private UrlCacheService urlCacheService;

    @Mock
    private SnowflakeIdGenerator snowflakeIdGenerator;

    @Mock
    private LynkProperties lynkProperties;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private UrlService urlService;

    @Test
    void shouldCreateUrlWithGeneratedShortcode() {
        when(snowflakeIdGenerator.nextId()).thenReturn(123456789L);
        when(lynkProperties.url()).thenReturn(new LynkProperties.UrlProperties(Duration.ofDays(365)));
        when(urlRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var request = new CreateUrlRequest("https://example.com", null, null);
        UrlResponse response = urlService.createUrl(request, "user-1");

        assertThat(response.shortcode()).isNotBlank();
        assertThat(response.originalUrl()).isEqualTo("https://example.com");
        assertThat(response.expiresAt()).isAfter(Instant.now());
        verify(urlCacheService).cacheUrl(eq(response.shortcode()), eq("https://example.com"), any(Duration.class));
    }

    @Test
    void shouldCreateUrlWithCustomAlias() {
        when(snowflakeIdGenerator.nextId()).thenReturn(123456789L);
        when(urlRepository.existsByShortcode("my-alias")).thenReturn(false);
        when(lynkProperties.url()).thenReturn(new LynkProperties.UrlProperties(Duration.ofDays(365)));
        when(urlRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var request = new CreateUrlRequest("https://example.com", "my-alias", null);
        UrlResponse response = urlService.createUrl(request, "user-1");

        assertThat(response.shortcode()).isEqualTo("my-alias");
    }

    @Test
    void shouldThrowWhenAliasAlreadyExists() {
        when(urlRepository.existsByShortcode("taken")).thenReturn(true);

        var request = new CreateUrlRequest("https://example.com", "taken", null);

        assertThatThrownBy(() -> urlService.createUrl(request, "user-1"))
                .isInstanceOf(AliasAlreadyExistsException.class);
    }

    @Test
    void shouldResolveUrlFromCache() {
        when(urlCacheService.getCachedUrl("abc")).thenReturn(Optional.of("https://example.com"));

        String result = urlService.resolveAndTrack("abc", "1.2.3.4", "Mozilla", "https://google.com");

        assertThat(result).isEqualTo("https://example.com");
        verify(urlRepository, never()).findByShortcode(anyString());
        verify(eventPublisher).publishEvent(any(ClickEvent.class));
    }

    @Test
    void shouldResolveUrlFromDbOnCacheMiss() {
        when(urlCacheService.getCachedUrl("abc")).thenReturn(Optional.empty());
        var entity = UrlEntity.builder()
                .id(1L)
                .shortcode("abc")
                .originalUrl("https://example.com")
                .userId("user-1")
                .expiresAt(Instant.now().plusSeconds(3600))
                .createdAt(Instant.now())
                .build();
        when(urlRepository.findByShortcode("abc")).thenReturn(Optional.of(entity));

        String result = urlService.resolveAndTrack("abc", "1.2.3.4", "Mozilla", null);

        assertThat(result).isEqualTo("https://example.com");
        verify(urlCacheService).cacheUrl(eq("abc"), eq("https://example.com"), any(Duration.class));
    }

    @Test
    void shouldThrowWhenUrlNotFound() {
        when(urlCacheService.getCachedUrl("nope")).thenReturn(Optional.empty());
        when(urlRepository.findByShortcode("nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> urlService.resolveAndTrack("nope", "1.2.3.4", "Mozilla", null))
                .isInstanceOf(UrlNotFoundException.class);
    }

    @Test
    void shouldThrowWhenUrlExpired() {
        when(urlCacheService.getCachedUrl("old")).thenReturn(Optional.empty());
        var entity = UrlEntity.builder()
                .id(1L)
                .shortcode("old")
                .originalUrl("https://example.com")
                .userId("user-1")
                .expiresAt(Instant.now().minusSeconds(3600))
                .createdAt(Instant.now().minusSeconds(7200))
                .build();
        when(urlRepository.findByShortcode("old")).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> urlService.resolveAndTrack("old", "1.2.3.4", "Mozilla", null))
                .isInstanceOf(UrlExpiredException.class);
    }

    @Test
    void shouldPublishClickEventOnResolve() {
        when(urlCacheService.getCachedUrl("abc")).thenReturn(Optional.of("https://example.com"));

        urlService.resolveAndTrack("abc", "1.2.3.4", "Mozilla/5.0", "https://google.com");

        ArgumentCaptor<ClickEvent> captor = ArgumentCaptor.forClass(ClickEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        ClickEvent event = captor.getValue();
        assertThat(event.shortcode()).isEqualTo("abc");
        assertThat(event.ipAddress()).isEqualTo("1.2.3.4");
        assertThat(event.userAgent()).isEqualTo("Mozilla/5.0");
        assertThat(event.referer()).isEqualTo("https://google.com");
    }

    @Test
    void shouldDeleteUrlAndEvictCache() {
        var entity = UrlEntity.builder()
                .id(1L)
                .shortcode("abc")
                .originalUrl("https://example.com")
                .userId("user-1")
                .expiresAt(Instant.now().plusSeconds(3600))
                .createdAt(Instant.now())
                .build();
        when(urlRepository.findByShortcode("abc")).thenReturn(Optional.of(entity));

        urlService.deleteUrl("abc", "user-1");

        verify(urlRepository).delete(entity);
        verify(urlCacheService).evict("abc");
    }

    @Test
    void shouldThrowWhenDeletingOtherUsersUrl() {
        var entity = UrlEntity.builder()
                .id(1L)
                .shortcode("abc")
                .originalUrl("https://example.com")
                .userId("user-1")
                .expiresAt(Instant.now().plusSeconds(3600))
                .createdAt(Instant.now())
                .build();
        when(urlRepository.findByShortcode("abc")).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> urlService.deleteUrl("abc", "user-2")).isInstanceOf(UrlOwnershipException.class);
    }
}
