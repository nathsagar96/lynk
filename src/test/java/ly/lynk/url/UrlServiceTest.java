package ly.lynk.url;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import ly.lynk.common.UrlProperties;
import ly.lynk.common.exception.AliasAlreadyExistsException;
import ly.lynk.common.exception.UrlOwnershipException;
import ly.lynk.shortcode.SnowflakeIdGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UrlServiceTest {

    @Mock
    private UrlRepository urlRepository;

    @Mock
    private UrlCacheService urlCacheService;

    @Mock
    private SnowflakeIdGenerator snowflakeIdGenerator;

    @Mock
    private UrlProperties urlProperties;

    @InjectMocks
    private UrlService urlService;

    @Test
    void shouldCreateUrlWithGeneratedShortcode() {
        when(snowflakeIdGenerator.nextId()).thenReturn(123456789L);
        when(urlProperties.defaultExpiry()).thenReturn(Duration.ofDays(365));
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
        when(urlProperties.defaultExpiry()).thenReturn(Duration.ofDays(365));
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
