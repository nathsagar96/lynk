package ly.lynk.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ly.lynk.exception.AliasExpiredException;
import ly.lynk.exception.AliasNotFoundException;
import ly.lynk.service.UrlShortenerService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RedirectController.class)
class RedirectControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UrlShortenerService service;

    @Test
    @DisplayName("a GET redirect returns 302 with cache-prevention headers")
    void redirectsWithCacheHeaders() throws Exception {
        when(service.resolveAndTrack(eq("spring"), isNull(), eq(true))).thenReturn("https://spring.io");

        mockMvc.perform(get("/spring"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://spring.io"))
                .andExpect(header().string("Cache-Control", "no-cache, no-store, max-age=0, must-revalidate"))
                .andExpect(header().string("Pragma", "no-cache"));
    }

    @Test
    @DisplayName("incoming query parameters are forwarded to the target")
    void forwardsQueryParameters() throws Exception {
        when(service.resolveAndTrack(eq("spring"), eq("utm_source=twitter"), eq(true)))
                .thenReturn("https://spring.io?utm_source=twitter");

        mockMvc.perform(get("/spring").queryParam("utm_source", "twitter"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://spring.io?utm_source=twitter"));
    }

    @Test
    @DisplayName("a HEAD request resolves without tracking a click")
    void headDoesNotTrackClick() throws Exception {
        when(service.resolveAndTrack(eq("spring"), isNull(), eq(false))).thenReturn("https://spring.io");

        mockMvc.perform(head("/spring")).andExpect(status().isFound());

        verify(service).resolveAndTrack(eq("spring"), isNull(), eq(false));
        verify(service, never()).resolveAndTrack(eq("spring"), isNull(), eq(true));
    }

    @Test
    @DisplayName("a target carrying the client's malformed escape still redirects")
    void redirectsTargetWithRawPercent() throws Exception {
        when(service.resolveAndTrack(eq("spring"), eq("q=100%"), eq(true))).thenReturn("https://spring.io/docs?q=100%");

        mockMvc.perform(get("/spring").with(request -> {
                    request.setQueryString("q=100%");
                    return request;
                }))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://spring.io/docs?q=100%"));
    }

    @Test
    @DisplayName("an unknown alias surfaces as 404")
    void reportsNotFound() throws Exception {
        when(service.resolveAndTrack(eq("missing"), isNull(), eq(true)))
                .thenThrow(new AliasNotFoundException("missing"));

        mockMvc.perform(get("/missing"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    @DisplayName("an expired alias surfaces as 410")
    void reportsExpired() throws Exception {
        when(service.resolveAndTrack(eq("old"), isNull(), eq(true))).thenThrow(new AliasExpiredException("old"));

        mockMvc.perform(get("/old"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.title").exists());
    }
}
