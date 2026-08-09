package ly.lynk.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import ly.lynk.click.ClickEntity;
import ly.lynk.click.ClickRepository;
import ly.lynk.exception.UrlNotFoundException;
import ly.lynk.exception.UrlOwnershipException;
import ly.lynk.url.UrlEntity;
import ly.lynk.url.UrlRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {

    @Mock
    private UrlRepository urlRepository;

    @Mock
    private ClickRepository clickRepository;

    private AnalyticsService analyticsService;

    @BeforeEach
    void setUp() {
        analyticsService = new AnalyticsService(urlRepository, clickRepository);
    }

    @Test
    void getAnalytics_shouldReturnAnalyticsResponse_whenValidOwner() {
        String shortcode = "testCode";
        String userId = "user123";
        Instant now = Instant.now();
        Instant start = now.minus(7, ChronoUnit.DAYS);

        UrlEntity urlEntity = UrlEntity.builder()
                .shortcode(shortcode)
                .userId(userId)
                .originalUrl("https://example.com")
                .createdAt(start)
                .build();

        ClickEntity click1 = ClickEntity.builder()
                .shortcode(shortcode)
                .clickedAt(now)
                .referer("https://google.com")
                .userAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) Chrome/120.0")
                .build();

        when(urlRepository.findByShortcode(shortcode)).thenReturn(Optional.of(urlEntity));
        when(clickRepository.findByShortcodeAndClickedAtBetween(shortcode, start, now))
                .thenReturn(List.of(click1));

        AnalyticsResponse response = analyticsService.getAnalytics(shortcode, start, now, userId);

        assertThat(response.shortcode()).isEqualTo(shortcode);
        assertThat(response.totalClicks()).isEqualTo(1);
        assertThat(response.topBrowsers()).hasSize(1);
        assertThat(response.topBrowsers().get(0).browser()).isEqualTo("Chrome");
    }

    @Test
    void getAnalytics_shouldThrowUrlNotFoundException_whenShortcodeDoesNotExist() {
        when(urlRepository.findByShortcode("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> analyticsService.getAnalytics(
                        "missing", Instant.now().minusSeconds(100), Instant.now(), "user1"))
                .isInstanceOf(UrlNotFoundException.class);
    }

    @Test
    void getAnalytics_shouldThrowUrlOwnershipException_whenUserNotOwner() {
        UrlEntity urlEntity =
                UrlEntity.builder().shortcode("testCode").userId("ownerUser").build();

        when(urlRepository.findByShortcode("testCode")).thenReturn(Optional.of(urlEntity));

        assertThatThrownBy(() -> analyticsService.getAnalytics(
                        "testCode", Instant.now().minusSeconds(100), Instant.now(), "otherUser"))
                .isInstanceOf(UrlOwnershipException.class);
    }

    @Test
    void getAnalytics_shouldThrowIllegalArgumentException_whenStartDateAfterEndDate() {
        UrlEntity urlEntity =
                UrlEntity.builder().shortcode("testCode").userId("user1").build();

        Instant start = Instant.now();
        Instant end = start.minusSeconds(100);

        when(urlRepository.findByShortcode("testCode")).thenReturn(Optional.of(urlEntity));

        assertThatThrownBy(() -> analyticsService.getAnalytics("testCode", start, end, "user1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("startDate cannot be after endDate");
    }

    @Test
    void getAnalytics_shouldHandleEmptyClicksAndNullReferer() {
        String shortcode = "testCode";
        String userId = "user123";
        Instant now = Instant.now();
        Instant start = now.minus(1, ChronoUnit.DAYS);

        UrlEntity urlEntity =
                UrlEntity.builder().shortcode(shortcode).userId(userId).build();

        ClickEntity click1 = ClickEntity.builder()
                .shortcode(shortcode)
                .clickedAt(now)
                .referer(null)
                .userAgent(null)
                .build();

        when(urlRepository.findByShortcode(shortcode)).thenReturn(Optional.of(urlEntity));
        when(clickRepository.findByShortcodeAndClickedAtBetween(shortcode, start, now))
                .thenReturn(List.of(click1));

        AnalyticsResponse response = analyticsService.getAnalytics(shortcode, start, now, userId);

        assertThat(response.totalClicks()).isEqualTo(1);
        assertThat(response.topReferers()).hasSize(1);
        assertThat(response.topReferers().get(0).referer()).isEqualTo("Direct / None");
        assertThat(response.topBrowsers().get(0).browser()).isEqualTo("Unknown");
        assertThat(response.topOs().get(0).os()).isEqualTo("Unknown");
    }
}
