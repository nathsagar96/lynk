package ly.lynk.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import ly.lynk.click.ClickEntity;
import org.junit.jupiter.api.Test;

class AnalyticsMapperTest {

    private final AnalyticsMapper mapper = new AnalyticsMapper();

    @Test
    void toResponse_shouldMapClickEntitiesToAnalyticsResponse() {
        String shortcode = "testCode";
        Instant now = Instant.now();
        Instant start = now.minus(7, ChronoUnit.DAYS);

        ClickEntity click = ClickEntity.builder()
                .shortcode(shortcode)
                .clickedAt(now)
                .referer("https://github.com")
                .userAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) Chrome/120.0")
                .build();

        AnalyticsResponse response = mapper.toResponse(shortcode, start, now, List.of(click));

        assertThat(response.shortcode()).isEqualTo(shortcode);
        assertThat(response.startDate()).isEqualTo(start);
        assertThat(response.endDate()).isEqualTo(now);
        assertThat(response.totalClicks()).isEqualTo(1);
        assertThat(response.clicksOverTime()).hasSize(1);
        assertThat(response.topReferers()).containsExactly(new RefererStat("https://github.com", 1));
        assertThat(response.topBrowsers()).containsExactly(new BrowserStat("Chrome", 1));
        assertThat(response.topOs()).containsExactly(new OsStat("macOS", 1));
    }
}
