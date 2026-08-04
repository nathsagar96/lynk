package ly.lynk.url;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ly.lynk.common.exception.GlobalExceptionHandler;
import ly.lynk.common.exception.UrlNotFoundException;
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
}
