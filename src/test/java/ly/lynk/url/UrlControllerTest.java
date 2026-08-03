package ly.lynk.url;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import ly.lynk.common.exception.GlobalExceptionHandler;
import ly.lynk.common.exception.UrlNotFoundException;
import ly.lynk.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(UrlController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class})
class UrlControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UrlService urlService;

    @Test
    void shouldCreateShortUrl() throws Exception {
        var response = new UrlResponse(
                "abc12345678", "https://example.com", Instant.now().plusSeconds(86400), Instant.now());
        when(urlService.createUrl(any(), eq("user-1"))).thenReturn(response);

        mockMvc.perform(post("/api/v1/urls")
                        .with(jwt().jwt(j -> j.subject("user-1")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"url": "https://example.com"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shortcode").value("abc12345678"))
                .andExpect(jsonPath("$.originalUrl").value("https://example.com"));
    }

    @Test
    void shouldRejectCreateWithoutAuth() throws Exception {
        mockMvc.perform(post("/api/v1/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"url": "https://example.com"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectCreateWithInvalidUrl() throws Exception {
        mockMvc.perform(post("/api/v1/urls")
                        .with(jwt().jwt(j -> j.subject("user-1")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"url": "not-a-url"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRedirectForValidShortcode() throws Exception {
        when(urlService.resolveAndTrack(eq("abc"), any(), any(), any())).thenReturn("https://example.com");

        mockMvc.perform(get("/abc"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com"));
    }

    @Test
    void shouldReturn404ForUnknownShortcode() throws Exception {
        when(urlService.resolveAndTrack(eq("nope"), any(), any(), any())).thenThrow(new UrlNotFoundException("nope"));

        mockMvc.perform(get("/nope")).andExpect(status().isNotFound());
    }

    @Test
    void shouldListUrlsForAuthenticatedUser() throws Exception {
        when(urlService.listUrls(eq("user-1"), any(Pageable.class))).thenReturn(Page.empty());

        mockMvc.perform(get("/api/v1/urls").with(jwt().jwt(j -> j.subject("user-1"))))
                .andExpect(status().isOk());
    }

    @Test
    void shouldDeleteUrl() throws Exception {
        mockMvc.perform(delete("/api/v1/urls/abc").with(jwt().jwt(j -> j.subject("user-1"))))
                .andExpect(status().isNoContent());

        verify(urlService).deleteUrl("abc", "user-1");
    }

    @Test
    void shouldRejectDeleteWithoutAuth() throws Exception {
        mockMvc.perform(delete("/api/v1/urls/abc")).andExpect(status().isUnauthorized());
    }
}
