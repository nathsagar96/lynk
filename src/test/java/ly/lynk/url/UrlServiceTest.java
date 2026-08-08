package ly.lynk.url;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import ly.lynk.exception.AliasAlreadyExistsException;
import ly.lynk.exception.UrlNotFoundException;
import ly.lynk.exception.UrlOwnershipException;
import ly.lynk.shortcode.SnowflakeIdGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

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
        verify(urlCacheService, never()).evict(anyString());
    }

    @Test
    void shouldCreateUrlWithCustomExpiryOverridingDefault() {
        when(snowflakeIdGenerator.nextId()).thenReturn(123456789L);
        when(urlRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var request = new CreateUrlRequest("https://example.com", null, Duration.ofMinutes(5));
        UrlResponse response = urlService.createUrl(request, "user-1");

        assertThat(response.expiresAt())
                .isCloseTo(Instant.now().plus(Duration.ofMinutes(5)), within(2, ChronoUnit.SECONDS));
        verify(urlProperties, never()).defaultExpiry();
        verify(urlCacheService)
                .cacheUrl(eq(response.shortcode()), eq("https://example.com"), eq(Duration.ofMinutes(5)));
    }

    @Test
    void shouldGenerateShortcodeWhenAliasIsBlank() {
        when(snowflakeIdGenerator.nextId()).thenReturn(123456789L);
        when(urlProperties.defaultExpiry()).thenReturn(Duration.ofDays(365));
        when(urlRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var request = new CreateUrlRequest("https://example.com", "   ", null);
        UrlResponse response = urlService.createUrl(request, "user-1");

        assertThat(response.shortcode()).isNotEqualTo("   ");
        verify(urlRepository, never()).existsByShortcode(anyString());
    }

    @Test
    void shouldListUrlsForUser() {
        var entity = UrlEntity.builder()
                .id(1L)
                .shortcode("abc")
                .originalUrl("https://example.com")
                .userId("user-1")
                .expiresAt(Instant.now().plusSeconds(3600))
                .createdAt(Instant.now())
                .build();
        when(urlRepository.findByUserId("user-1", Pageable.unpaged())).thenReturn(new PageImpl<>(List.of(entity)));

        Page<UrlResponse> result = urlService.listUrls("user-1", Pageable.unpaged());

        assertThat(result.getContent()).hasSize(1);
        UrlResponse response = result.getContent().get(0);
        assertThat(response.shortcode()).isEqualTo("abc");
        assertThat(response.originalUrl()).isEqualTo("https://example.com");
        assertThat(response.expiresAt()).isEqualTo(entity.getExpiresAt());
        assertThat(response.createdAt()).isEqualTo(entity.getCreatedAt());
    }

    @Test
    void shouldThrowWhenDeletingUnknownUrl() {
        when(urlRepository.findByShortcode("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> urlService.deleteUrl("missing", "user-1")).isInstanceOf(UrlNotFoundException.class);
        verify(urlRepository, never()).delete(any());
        verify(urlCacheService, never()).evict(anyString());
    }

    @Test
    void shouldSaveEntityWithExpectedFields() {
        when(snowflakeIdGenerator.nextId()).thenReturn(42L);
        when(urlProperties.defaultExpiry()).thenReturn(Duration.ofDays(365));
        when(urlRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var request = new CreateUrlRequest("https://example.com", "my-alias", null);
        urlService.createUrl(request, "user-1");

        ArgumentCaptor<UrlEntity> captor = ArgumentCaptor.forClass(UrlEntity.class);
        verify(urlRepository).save(captor.capture());
        UrlEntity entity = captor.getValue();
        assertThat(entity.getId()).isEqualTo(42L);
        assertThat(entity.getShortcode()).isEqualTo("my-alias");
        assertThat(entity.getOriginalUrl()).isEqualTo("https://example.com");
        assertThat(entity.getUserId()).isEqualTo("user-1");
        assertThat(entity.getExpiresAt()).isAfter(Instant.now());
        assertThat(entity.getCreatedAt()).isNotNull();
    }
}
