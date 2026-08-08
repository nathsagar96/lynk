package ly.lynk.url;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import ly.lynk.exception.AliasAlreadyExistsException;
import ly.lynk.exception.GlobalExceptionHandler;
import ly.lynk.exception.UrlNotFoundException;
import ly.lynk.exception.UrlOwnershipException;
import ly.lynk.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
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

    @MockitoBean
    private RedirectService redirectService;

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

    @Test
    void shouldReturnConflictWhenAliasAlreadyTaken() throws Exception {
        when(urlService.createUrl(any(), eq("user-1"))).thenThrow(new AliasAlreadyExistsException("taken"));

        mockMvc.perform(post("/api/v1/urls")
                        .with(jwt().jwt(j -> j.subject("user-1")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"url": "https://example.com", "alias": "taken"}
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldReturnNotFoundWhenDeletingUnknownUrl() throws Exception {
        doThrow(new UrlNotFoundException("missing")).when(urlService).deleteUrl("missing", "user-1");

        mockMvc.perform(delete("/api/v1/urls/missing").with(jwt().jwt(j -> j.subject("user-1"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturnForbiddenWhenDeletingOthersUrl() throws Exception {
        doThrow(new UrlOwnershipException()).when(urlService).deleteUrl("abc", "user-1");

        mockMvc.perform(delete("/api/v1/urls/abc").with(jwt().jwt(j -> j.subject("user-1"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldBindAliasAndExpiryFromRequestBody() throws Exception {
        var response =
                new UrlResponse("my-alias", "https://example.com", Instant.now().plusSeconds(600), Instant.now());
        when(urlService.createUrl(any(), eq("user-1"))).thenReturn(response);

        mockMvc.perform(post("/api/v1/urls")
                        .with(jwt().jwt(j -> j.subject("user-1")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"url": "https://example.com", "alias": "my-alias", "expiry": "PT10M"}
                                """))
                .andExpect(status().isCreated());

        ArgumentCaptor<CreateUrlRequest> captor = ArgumentCaptor.forClass(CreateUrlRequest.class);
        verify(urlService).createUrl(captor.capture(), eq("user-1"));
        assertThat(captor.getValue().alias()).isEqualTo("my-alias");
        assertThat(captor.getValue().expiry()).isEqualTo(Duration.ofMinutes(10));
    }

    @Test
    void shouldRejectCreateWithInvalidAlias() throws Exception {
        mockMvc.perform(post("/api/v1/urls")
                        .with(jwt().jwt(j -> j.subject("user-1")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"url": "https://example.com", "alias": "a!"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturnListedUrlsInBody() throws Exception {
        var response =
                new UrlResponse("abc", "https://example.com", Instant.now().plusSeconds(86400), Instant.now());
        when(urlService.listUrls(eq("user-1"), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(response)));

        mockMvc.perform(get("/api/v1/urls").with(jwt().jwt(j -> j.subject("user-1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].shortcode").value("abc"))
                .andExpect(jsonPath("$.content[0].originalUrl").value("https://example.com"));
    }

    @Test
    void shouldRejectListWithoutAuth() throws Exception {
        mockMvc.perform(get("/api/v1/urls")).andExpect(status().isUnauthorized());
    }
}
