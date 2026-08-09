package ly.lynk.analytics;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import ly.lynk.exception.GlobalExceptionHandler;
import ly.lynk.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AnalyticsController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class})
class AnalyticsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AnalyticsService analyticsService;

    @Test
    void getAnalytics_shouldReturn200AndAnalyticsData() throws Exception {
        String shortcode = "abc12";
        Instant now = Instant.now();
        Instant start = now.minusSeconds(3600);

        AnalyticsResponse mockResponse =
                new AnalyticsResponse(shortcode, start, now, 10L, List.of(), List.of(), List.of(), List.of());

        when(analyticsService.getAnalytics(eq(shortcode), any(), any(), eq("user123")))
                .thenReturn(mockResponse);

        mockMvc.perform(get("/api/v1/urls/" + shortcode + "/analytics")
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt -> jwt.subject("user123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shortcode").value(shortcode))
                .andExpect(jsonPath("$.totalClicks").value(10));
    }
}
