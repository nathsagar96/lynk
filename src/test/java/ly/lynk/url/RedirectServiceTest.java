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
import ly.lynk.exception.UrlExpiredException;
import ly.lynk.exception.UrlNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class RedirectServiceTest {

    @Mock
    private UrlRepository urlRepository;

    @Mock
    private UrlCacheService urlCacheService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private RedirectService redirectService;

    @Test
    void shouldResolveUrlFromCache() {
        when(urlCacheService.getCachedUrl("abc")).thenReturn(Optional.of("https://example.com"));

        String result = redirectService.resolveAndTrack("abc", "1.2.3.4", "Mozilla", "https://google.com");

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

        String result = redirectService.resolveAndTrack("abc", "1.2.3.4", "Mozilla", null);

        assertThat(result).isEqualTo("https://example.com");
        verify(urlCacheService).cacheUrl(eq("abc"), eq("https://example.com"), any(Duration.class));
    }

    @Test
    void shouldThrowWhenUrlNotFound() {
        when(urlCacheService.getCachedUrl("nope")).thenReturn(Optional.empty());
        when(urlRepository.findByShortcode("nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> redirectService.resolveAndTrack("nope", "1.2.3.4", "Mozilla", null))
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

        assertThatThrownBy(() -> redirectService.resolveAndTrack("old", "1.2.3.4", "Mozilla", null))
                .isInstanceOf(UrlExpiredException.class);
    }

    @Test
    void shouldPublishClickEventOnResolve() {
        when(urlCacheService.getCachedUrl("abc")).thenReturn(Optional.of("https://example.com"));

        redirectService.resolveAndTrack("abc", "1.2.3.4", "Mozilla/5.0", "https://google.com");

        ArgumentCaptor<ClickEvent> captor = ArgumentCaptor.forClass(ClickEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        ClickEvent event = captor.getValue();
        assertThat(event.shortcode()).isEqualTo("abc");
        assertThat(event.ipAddress()).isEqualTo("1.2.3.4");
        assertThat(event.userAgent()).isEqualTo("Mozilla/5.0");
        assertThat(event.referer()).isEqualTo("https://google.com");
    }

    @Test
    void shouldNotPublishEventWhenUrlNotFound() {
        when(urlCacheService.getCachedUrl("nope")).thenReturn(Optional.empty());
        when(urlRepository.findByShortcode("nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> redirectService.resolveAndTrack("nope", "1.2.3.4", "Mozilla", null))
                .isInstanceOf(UrlNotFoundException.class);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void shouldNotCacheWhenResolvedFromCache() {
        when(urlCacheService.getCachedUrl("abc")).thenReturn(Optional.of("https://example.com"));

        redirectService.resolveAndTrack("abc", "1.2.3.4", "Mozilla", null);

        verify(urlCacheService, never()).cacheUrl(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void shouldPublishClickEventWhenResolvedFromDb() {
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

        redirectService.resolveAndTrack("abc", "10.0.0.1", "curl/8.0", "https://referrer.example");

        ArgumentCaptor<ClickEvent> captor = ArgumentCaptor.forClass(ClickEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().shortcode()).isEqualTo("abc");
        assertThat(captor.getValue().ipAddress()).isEqualTo("10.0.0.1");
        assertThat(captor.getValue().userAgent()).isEqualTo("curl/8.0");
        assertThat(captor.getValue().referer()).isEqualTo("https://referrer.example");
    }

    @Test
    void shouldNotCacheOrTrackWhenUrlExpired() {
        when(urlCacheService.getCachedUrl("old")).thenReturn(Optional.empty());
        var entity = UrlEntity.builder()
                .id(1L)
                .shortcode("old")
                .originalUrl("https://example.com")
                .userId("user-1")
                .expiresAt(Instant.now().minusSeconds(60))
                .createdAt(Instant.now().minusSeconds(120))
                .build();
        when(urlRepository.findByShortcode("old")).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> redirectService.resolveAndTrack("old", "1.2.3.4", "Mozilla", null))
                .isInstanceOf(UrlExpiredException.class);
        verify(urlCacheService, never()).cacheUrl(anyString(), anyString(), any(Duration.class));
        verify(eventPublisher, never()).publishEvent(any());
    }
}
