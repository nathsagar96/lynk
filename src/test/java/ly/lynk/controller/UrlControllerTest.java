package ly.lynk.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import ly.lynk.TestSecurityConfiguration;
import ly.lynk.config.SecurityConfig;
import ly.lynk.dto.QrCodePayload;
import ly.lynk.dto.ShortenUrlRequest;
import ly.lynk.dto.ShortenUrlResponse;
import ly.lynk.dto.UrlStatsResponse;
import ly.lynk.exception.AliasAlreadyExistsException;
import ly.lynk.exception.AliasExpiredException;
import ly.lynk.exception.AliasGenerationException;
import ly.lynk.exception.AliasNotFoundException;
import ly.lynk.exception.ForbiddenException;
import ly.lynk.exception.InvalidUrlException;
import ly.lynk.exception.QrCodeGenerationException;
import ly.lynk.exception.ReservedAliasException;
import ly.lynk.service.UrlShortenerService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(UrlController.class)
@Import({SecurityConfig.class, TestSecurityConfiguration.class})
class UrlControllerTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final String USER_ID = "00000000-0000-0000-0000-000000000001";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UrlShortenerService service;

    private ShortenUrlResponse response(String alias) {
        return new ShortenUrlResponse(alias, "https://spring.io", "http://localhost:8080/" + alias, NOW, null, USER_ID);
    }

    @Test
    @DisplayName("creating a short URL without authentication returns 401 Unauthorized")
    void rejectsUnauthenticatedCreation() throws Exception {
        mockMvc.perform(post("/api/v1/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {"originalUrl":"https://spring.io","customAlias":"spring"}"""))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("creating a short URL with token missing subject returns 403 Forbidden")
    void rejectsCreationWhenTokenMissingSubject() throws Exception {
        mockMvc.perform(post("/api/v1/urls")
                        .with(jwt().jwt(builder -> builder.claims(claims -> claims.remove("sub"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {"originalUrl":"https://spring.io","customAlias":"spring"}"""))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("creating a short URL returns 201 with a Location header and ownerId")
    void createsShortUrl() throws Exception {
        when(service.shortenUrl(any(ShortenUrlRequest.class), eq(USER_ID))).thenReturn(response("spring"));

        mockMvc.perform(post("/api/v1/urls")
                        .with(jwt().jwt(builder -> builder.subject(USER_ID)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {"originalUrl":"https://spring.io","customAlias":"spring"}"""))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost:8080/api/v1/urls/spring"))
                .andExpect(jsonPath("$.alias").value("spring"))
                .andExpect(jsonPath("$.originalUrl").value("https://spring.io"))
                .andExpect(jsonPath("$.shortUrl").value("http://localhost:8080/spring"))
                .andExpect(jsonPath("$.ownerId").value(USER_ID));
    }

    @Test
    @DisplayName("the Location header uses the configured base URL, not the request host")
    void locationUsesConfiguredBaseUrl() throws Exception {
        when(service.shortenUrl(any(ShortenUrlRequest.class), any())).thenReturn(response("spring"));

        mockMvc.perform(post("/api/v1/urls")
                        .with(jwt().jwt(builder -> builder.subject(USER_ID)))
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
                        .with(jwt().jwt(builder -> builder.subject(USER_ID)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {"originalUrl":"not-a-url"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors.originalUrl").exists())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("a blank custom alias is treated as omitted and auto-generates an alias")
    void acceptsBlankCustomAlias() throws Exception {
        when(service.shortenUrl(any(ShortenUrlRequest.class), any())).thenReturn(response("auto123"));

        mockMvc.perform(post("/api/v1/urls")
                        .with(jwt().jwt(builder -> builder.subject(USER_ID)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {"originalUrl":"https://spring.io","customAlias":""}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.alias").value("auto123"));
    }

    @Test
    @DisplayName("a malformed custom alias is rejected with a field error")
    void rejectsInvalidCustomAlias() throws Exception {
        mockMvc.perform(post("/api/v1/urls")
                        .with(jwt().jwt(builder -> builder.subject(USER_ID)))
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
                        .with(jwt().jwt(builder -> builder.subject(USER_ID)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    @DisplayName("an unsupported media type is rejected")
    void rejectsUnsupportedMediaType() throws Exception {
        mockMvc.perform(post("/api/v1/urls")
                        .with(jwt().jwt(builder -> builder.subject(USER_ID)))
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("https://spring.io"))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    @DisplayName("an unsupported method is rejected")
    void rejectsUnsupportedMethod() throws Exception {
        mockMvc.perform(post("/api/v1/urls/spring").with(jwt().jwt(builder -> builder.subject(USER_ID))))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    @DisplayName("a conflicting alias surfaces as 409")
    void reportsConflict() throws Exception {
        when(service.shortenUrl(any(ShortenUrlRequest.class), any()))
                .thenThrow(new AliasAlreadyExistsException("spring"));

        mockMvc.perform(post("/api/v1/urls")
                        .with(jwt().jwt(builder -> builder.subject(USER_ID)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {"originalUrl":"https://spring.io","customAlias":"spring"}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").exists());
    }

    @Test
    @DisplayName("a reserved alias surfaces as 400")
    void reportsReservedAlias() throws Exception {
        when(service.shortenUrl(any(ShortenUrlRequest.class), any())).thenThrow(new ReservedAliasException("api"));

        mockMvc.perform(post("/api/v1/urls")
                        .with(jwt().jwt(builder -> builder.subject(USER_ID)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {"originalUrl":"https://spring.io","customAlias":"api"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("an invalid destination surfaces as 400")
    void reportsInvalidUrl() throws Exception {
        when(service.shortenUrl(any(ShortenUrlRequest.class), any()))
                .thenThrow(new InvalidUrlException("originalUrl must not point back to its own short URL"));

        mockMvc.perform(post("/api/v1/urls")
                        .with(jwt().jwt(builder -> builder.subject(USER_ID)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {"originalUrl":"https://spring.io"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("an alias generation failure surfaces as 500")
    void reportsGenerationFailure() throws Exception {
        when(service.shortenUrl(any(ShortenUrlRequest.class), any()))
                .thenThrow(new AliasGenerationException("Unable to generate alias"));

        mockMvc.perform(post("/api/v1/urls")
                        .with(jwt().jwt(builder -> builder.subject(USER_ID)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {"originalUrl":"https://spring.io"}"""))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("https://lynk.ly/problems/alias-generation-failed"));
    }

    @Test
    @DisplayName("an unexpected exception surfaces as 500 internal server error")
    void reportsUnexpectedError() throws Exception {
        when(service.shortenUrl(any(ShortenUrlRequest.class), any()))
                .thenThrow(new RuntimeException("Database unreachable"));

        mockMvc.perform(post("/api/v1/urls")
                        .with(jwt().jwt(builder -> builder.subject(USER_ID)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {"originalUrl":"https://spring.io"}"""))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("https://lynk.ly/problems/internal-server-error"));
    }

    @Test
    @DisplayName("shortening a URL with a future expiration returns 201 with expiresAt")
    void createsShortUrlWithExpiration() throws Exception {
        Instant expiresAt = NOW.plusSeconds(3600);
        when(service.shortenUrl(any(ShortenUrlRequest.class), any()))
                .thenReturn(new ShortenUrlResponse(
                        "spring", "https://spring.io", "http://localhost:8080/spring", NOW, expiresAt, USER_ID));

        mockMvc.perform(post("/api/v1/urls")
                        .with(jwt().jwt(builder -> builder.subject(USER_ID)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {"originalUrl":"https://spring.io","expiresAt":"2099-01-01T00:00:00Z"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.expiresAt").value(expiresAt.toString()))
                .andExpect(jsonPath("$.ownerId").value(USER_ID));
    }

    @Test
    @DisplayName("details are returned publicly without ownerId or click count")
    void returnsDetails() throws Exception {
        // Public endpoint returns ownerId as null
        when(service.getUrlDetails("spring"))
                .thenReturn(new ShortenUrlResponse(
                        "spring", "https://spring.io", "http://localhost:8080/spring", NOW, null, null));

        mockMvc.perform(get("/api/v1/urls/spring"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alias").value("spring"))
                .andExpect(jsonPath("$.ownerId").doesNotExist())
                .andExpect(jsonPath("$.clickCount").doesNotExist());
    }

    @Test
    @DisplayName("HEAD metadata request without authentication returns 200")
    void allowsUnauthenticatedHeadOnDetails() throws Exception {
        when(service.getUrlDetails("spring"))
                .thenReturn(new ShortenUrlResponse(
                        "spring", "https://spring.io", "http://localhost:8080/spring", NOW, null, null));

        mockMvc.perform(head("/api/v1/urls/spring")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("listing owned URLs without authentication returns 401")
    void rejectsUnauthenticatedListing() throws Exception {
        mockMvc.perform(get("/api/v1/urls")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("listing owned URLs with token missing subject returns 403 Forbidden")
    void rejectsListingWhenTokenMissingSubject() throws Exception {
        mockMvc.perform(get("/api/v1/urls").with(jwt().jwt(builder -> builder.claims(claims -> claims.remove("sub")))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("listing owned URLs returns paginated results with default sort")
    void listsOwnedUrls() throws Exception {
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        when(service.getUserUrls(eq(USER_ID), captor.capture()))
                .thenReturn(new PageImpl<>(List.of(response("spring")), PageRequest.of(0, 10), 1));

        mockMvc.perform(get("/api/v1/urls")
                        .with(jwt().jwt(builder -> builder.subject(USER_ID)))
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].alias").value("spring"))
                .andExpect(jsonPath("$.content[0].ownerId").value(USER_ID))
                .andExpect(jsonPath("$.totalElements").value(1));

        assertThat(captor.getValue().getSort().getOrderFor("id").isDescending()).isTrue();
    }

    @Test
    @DisplayName("querying statistics without authentication returns 401")
    void rejectsUnauthenticatedStats() throws Exception {
        mockMvc.perform(get("/api/v1/urls/spring/stats")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("querying statistics by owner returns stats with click count")
    void returnsStats() throws Exception {
        when(service.getStats(eq("spring"), eq(USER_ID)))
                .thenReturn(new UrlStatsResponse(
                        "spring", "https://spring.io", "http://localhost:8080/spring", 7L, NOW, null, USER_ID));

        mockMvc.perform(get("/api/v1/urls/spring/stats").with(jwt().jwt(builder -> builder.subject(USER_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clickCount").value(7))
                .andExpect(jsonPath("$.ownerId").value(USER_ID));
    }

    @Test
    @DisplayName("querying statistics by non-owner returns 403 Forbidden")
    void rejectsNonOwnerStats() throws Exception {
        when(service.getStats(eq("spring"), any()))
                .thenThrow(new ForbiddenException("You do not have permission to access or modify this URL"));

        mockMvc.perform(get("/api/v1/urls/spring/stats").with(jwt().jwt(builder -> builder.subject("other-user"))))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("https://lynk.ly/problems/forbidden"));
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
    @DisplayName("deleting a short URL without authentication returns 401")
    void rejectsUnauthenticatedDelete() throws Exception {
        mockMvc.perform(delete("/api/v1/urls/spring")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("deleting a short URL by owner returns 204")
    void deletesShortUrl() throws Exception {
        mockMvc.perform(delete("/api/v1/urls/spring").with(jwt().jwt(builder -> builder.subject(USER_ID))))
                .andExpect(status().isNoContent());

        verify(service).deleteUrl("spring", USER_ID);
    }

    @Test
    @DisplayName("deleting a short URL by non-owner returns 403 Forbidden")
    void rejectsNonOwnerDelete() throws Exception {
        Mockito.doThrow(new ForbiddenException("You do not have permission to access or modify this URL"))
                .when(service)
                .deleteUrl(eq("spring"), eq("other-user"));

        mockMvc.perform(delete("/api/v1/urls/spring").with(jwt().jwt(builder -> builder.subject("other-user"))))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("https://lynk.ly/problems/forbidden"));
    }

    @Test
    @DisplayName("CORS preflight request from allowed origin returns 200 with allow-origin header")
    void allowsCorsPreflight() throws Exception {
        mockMvc.perform(options("/api/v1/urls")
                        .header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"));
    }

    @Test
    @DisplayName("CORS responses expose the Location header to browser clients")
    void exposesLocationHeaderToCorsClients() throws Exception {
        when(service.shortenUrl(any(ShortenUrlRequest.class), any())).thenReturn(response("spring"));

        mockMvc.perform(post("/api/v1/urls")
                        .header("Origin", "http://localhost:3000")
                        .with(jwt().jwt(builder -> builder.subject(USER_ID)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {"originalUrl":"https://spring.io","customAlias":"spring"}"""))
                .andExpect(status().isCreated())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"))
                .andExpect(header().string("Access-Control-Expose-Headers", "Location"));
    }

    @Test
    @DisplayName("retrieving QR code without authentication returns 200 with PNG and cache headers")
    void retrievesQrCodeSuccessfully() throws Exception {
        byte[] fakePng = new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47};
        when(service.getQrCode("spring", 256)).thenReturn(new QrCodePayload(fakePng, Duration.ofDays(1)));

        mockMvc.perform(get("/api/v1/urls/spring/qr"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(header().string("Content-Disposition", "inline"))
                .andExpect(header().string("Cache-Control", "max-age=86400, public"))
                .andExpect(content().bytes(fakePng));
    }

    @Test
    @DisplayName("retrieving QR code with custom size passes parameter to service")
    void retrievesQrCodeCustomSize() throws Exception {
        byte[] fakePng = new byte[] {1, 2, 3};
        when(service.getQrCode("spring", 512)).thenReturn(new QrCodePayload(fakePng, Duration.ofSeconds(1800)));

        mockMvc.perform(get("/api/v1/urls/spring/qr").param("size", "512"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(header().string("Cache-Control", "max-age=1800, public"))
                .andExpect(content().bytes(fakePng));
    }

    @Test
    @DisplayName("retrieving QR code with zero TTL returns no-store cache control")
    void retrievesQrCodeZeroTtl() throws Exception {
        byte[] fakePng = new byte[] {1, 2, 3};
        when(service.getQrCode("spring", 256)).thenReturn(new QrCodePayload(fakePng, Duration.ZERO));

        mockMvc.perform(get("/api/v1/urls/spring/qr"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().bytes(fakePng));
    }

    @Test
    @DisplayName("retrieving QR code for non-existent alias returns 404 Not Found")
    void retrievesQrCodeNotFound() throws Exception {
        when(service.getQrCode("missing", 256)).thenThrow(new AliasNotFoundException("missing"));

        mockMvc.perform(get("/api/v1/urls/missing/qr"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("https://lynk.ly/problems/alias-not-found"));
    }

    @Test
    @DisplayName("retrieving QR code for expired alias returns 410 Gone")
    void retrievesQrCodeExpired() throws Exception {
        when(service.getQrCode("expired", 256)).thenThrow(new AliasExpiredException("expired"));

        mockMvc.perform(get("/api/v1/urls/expired/qr"))
                .andExpect(status().isGone())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("https://lynk.ly/problems/alias-expired"));
    }

    @Test
    @DisplayName("retrieving QR code with size below minimum returns 400 Bad Request")
    void rejectsSizeTooSmall() throws Exception {
        mockMvc.perform(get("/api/v1/urls/spring/qr").param("size", "32")).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("retrieving QR code with size above maximum returns 400 Bad Request")
    void rejectsSizeTooLarge() throws Exception {
        mockMvc.perform(get("/api/v1/urls/spring/qr").param("size", "2048")).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("generation failure returns 500 ProblemDetail")
    void handlesQrCodeGenerationFailure() throws Exception {
        when(service.getQrCode("spring", 256))
                .thenThrow(new QrCodeGenerationException("Generation failed", new RuntimeException("boom")));

        mockMvc.perform(get("/api/v1/urls/spring/qr"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("https://lynk.ly/problems/qr-generation-failed"));
    }
}
