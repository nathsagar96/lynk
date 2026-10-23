package ly.lynk.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Set;
import ly.lynk.config.AppProperties;
import ly.lynk.dto.ShortenUrlRequest;
import ly.lynk.dto.ShortenUrlResponse;
import ly.lynk.dto.UrlStatsResponse;
import ly.lynk.exception.AliasAlreadyExistsException;
import ly.lynk.exception.AliasNotFoundException;
import ly.lynk.exception.InvalidUrlException;
import ly.lynk.exception.ReservedAliasException;
import ly.lynk.service.UrlShortenerService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(UrlController.class)
class UrlControllerTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UrlShortenerService service;

    @TestConfiguration
    static class AppPropertiesConfig {

        @Bean
        AppProperties appProperties() {
            return new AppProperties("http://localhost:8080", 7, 5, Set.of("api", "actuator", "error"));
        }
    }

    private ShortenUrlResponse response(String alias) {
        return new ShortenUrlResponse(alias, "https://spring.io", "http://localhost:8080/" + alias, NOW, null);
    }

    @Test
    @DisplayName("creating a short URL returns 201 with a Location header")
    void createsShortUrl() throws Exception {
        when(service.shortenUrl(any(ShortenUrlRequest.class))).thenReturn(response("spring"));

        mockMvc.perform(post("/api/v1/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {"originalUrl":"https://spring.io","customAlias":"spring"}"""))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost:8080/api/v1/urls/spring"))
                .andExpect(jsonPath("$.alias").value("spring"))
                .andExpect(jsonPath("$.originalUrl").value("https://spring.io"))
                .andExpect(jsonPath("$.shortUrl").value("http://localhost:8080/spring"));
    }

    @Test
    @DisplayName("the Location header uses the configured base URL, not the request host")
    void locationUsesConfiguredBaseUrl() throws Exception {
        when(service.shortenUrl(any(ShortenUrlRequest.class))).thenReturn(response("spring"));

        mockMvc.perform(post("/api/v1/urls")
                        .with(request -> {
                            request.setServerName("evil.example.com");
                            request.setServerPort(9999);
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {"originalUrl":"https://spring.io","customAlias":"spring"}"""))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost:8080/api/v1/urls/spring"));
    }

    @Test
    @DisplayName("an invalid URL is rejected with an RFC 9457 problem detail")
    void rejectsInvalidUrl() throws Exception {
        mockMvc.perform(post("/api/v1/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {"originalUrl":"not-a-url"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors.originalUrl").exists())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("a malformed custom alias is rejected with a field error")
    void rejectsInvalidCustomAlias() throws Exception {
        mockMvc.perform(post("/api/v1/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {"originalUrl":"https://spring.io","customAlias":"a b"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.customAlias").exists());
    }

    @Test
    @DisplayName("an unparseable body is rejected as a malformed request")
    void rejectsMalformedBody() throws Exception {
        mockMvc.perform(post("/api/v1/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    @DisplayName("an unsupported media type is rejected")
    void rejectsUnsupportedMediaType() throws Exception {
        mockMvc.perform(post("/api/v1/urls").contentType(MediaType.TEXT_PLAIN).content("https://spring.io"))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    @DisplayName("an unsupported method is rejected")
    void rejectsUnsupportedMethod() throws Exception {
        mockMvc.perform(post("/api/v1/urls/spring")).andExpect(status().isMethodNotAllowed());
    }

    @Test
    @DisplayName("a conflicting alias surfaces as 409")
    void reportsConflict() throws Exception {
        when(service.shortenUrl(any(ShortenUrlRequest.class))).thenThrow(new AliasAlreadyExistsException("spring"));

        mockMvc.perform(post("/api/v1/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {"originalUrl":"https://spring.io","customAlias":"spring"}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").exists());
    }

    @Test
    @DisplayName("a reserved alias surfaces as 400")
    void reportsReservedAlias() throws Exception {
        when(service.shortenUrl(any(ShortenUrlRequest.class))).thenThrow(new ReservedAliasException("api"));

        mockMvc.perform(post("/api/v1/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {"originalUrl":"https://spring.io","customAlias":"api"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("an invalid destination surfaces as 400")
    void reportsInvalidUrl() throws Exception {
        when(service.shortenUrl(any(ShortenUrlRequest.class)))
                .thenThrow(new InvalidUrlException("originalUrl must not point back to its own short URL"));

        mockMvc.perform(post("/api/v1/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {"originalUrl":"https://spring.io"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("details are returned without a click count")
    void returnsDetails() throws Exception {
        when(service.getUrlDetails(anyString())).thenReturn(response("spring"));

        mockMvc.perform(get("/api/v1/urls/spring"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alias").value("spring"))
                .andExpect(jsonPath("$.clickCount").doesNotExist());
    }

    @Test
    @DisplayName("statistics are returned with a click count")
    void returnsStats() throws Exception {
        when(service.getStats(anyString()))
                .thenReturn(new UrlStatsResponse(
                        "spring", "https://spring.io", "http://localhost:8080/spring", 7L, NOW, null));

        mockMvc.perform(get("/api/v1/urls/spring/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clickCount").value(7));
    }

    @Test
    @DisplayName("an unknown alias surfaces as 404")
    void reportsNotFound() throws Exception {
        when(service.getUrlDetails(anyString())).thenThrow(new AliasNotFoundException("nope"));

        mockMvc.perform(get("/api/v1/urls/nope"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    @DisplayName("deleting a short URL returns 204")
    void deletesShortUrl() throws Exception {
        mockMvc.perform(delete("/api/v1/urls/spring")).andExpect(status().isNoContent());

        verify(service).deleteUrl("spring");
    }
}
