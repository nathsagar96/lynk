package ly.lynk.url;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ly.lynk.exception.GlobalExceptionHandler;
import ly.lynk.exception.UrlExpiredException;
import ly.lynk.exception.UrlNotFoundException;
import ly.lynk.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RedirectController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class})
class RedirectControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RedirectService redirectService;

    @Test
    void shouldRedirectForValidShortcode() throws Exception {
        when(redirectService.resolveAndTrack(eq("abc"), any(), any(), any())).thenReturn("https://example.com");

        mockMvc.perform(get("/abc"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com"));
    }

    @Test
    void shouldReturn404ForUnknownShortcode() throws Exception {
        when(redirectService.resolveAndTrack(eq("nope"), any(), any(), any()))
                .thenThrow(new UrlNotFoundException("nope"));

        mockMvc.perform(get("/nope")).andExpect(status().isNotFound());
    }

    @Test
    void shouldReturn410ForExpiredShortcode() throws Exception {
        when(redirectService.resolveAndTrack(eq("old"), any(), any(), any())).thenThrow(new UrlExpiredException("old"));

        mockMvc.perform(get("/old")).andExpect(status().isGone());
    }

    @Test
    void shouldPassIpUserAgentAndRefererToService() throws Exception {
        when(redirectService.resolveAndTrack(eq("abc"), any(), any(), any())).thenReturn("https://example.com");

        mockMvc.perform(get("/abc")
                        .header("User-Agent", "curl/8.0")
                        .header("Referer", "https://referrer.example")
                        .remoteAddress("192.168.1.10"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com"));

        verify(redirectService).resolveAndTrack("abc", "192.168.1.10", "curl/8.0", "https://referrer.example");
    }
}
