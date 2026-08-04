package ly.lynk.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import ly.lynk.TestcontainersConfiguration;
import ly.lynk.click.ClickRepository;
import ly.lynk.url.UrlRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class UrlIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UrlRepository urlRepository;

    @Autowired
    private ClickRepository clickRepository;

    @BeforeEach
    void setUp() {
        clickRepository.deleteAll();
        urlRepository.deleteAll();
    }

    @Test
    void shouldCreateAndRedirect() throws Exception {
        // Create a short URL
        String responseBody = mockMvc.perform(post("/api/v1/urls")
                        .with(jwt().jwt(j -> j.subject("user-1")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"url": "https://example.com"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shortcode").isNotEmpty())
                .andExpect(jsonPath("$.originalUrl").value("https://example.com"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode json = objectMapper.readTree(responseBody);
        String shortcode = json.get("shortcode").asText();

        // Redirect using the shortcode
        mockMvc.perform(get("/" + shortcode))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com"));

        // Wait for async click event to persist
        await().atMost(Duration.ofSeconds(5)).until(() -> clickRepository.count() == 1L);

        // Verify click was recorded
        assertThat(clickRepository.count()).isEqualTo(1);
    }

    @Test
    void shouldCreateWithCustomAlias() throws Exception {
        mockMvc.perform(post("/api/v1/urls")
                        .with(jwt().jwt(j -> j.subject("user-1")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"url": "https://example.com", "alias": "my-link"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shortcode").value("my-link"));

        // Redirect using custom alias
        mockMvc.perform(get("/my-link"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com"));
    }

    @Test
    void shouldRejectDuplicateAlias() throws Exception {
        // Create first URL with alias
        mockMvc.perform(post("/api/v1/urls")
                        .with(jwt().jwt(j -> j.subject("user-1")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"url": "https://example.com", "alias": "taken"}
                                """))
                .andExpect(status().isCreated());

        // Try to create second URL with same alias
        mockMvc.perform(post("/api/v1/urls")
                        .with(jwt().jwt(j -> j.subject("user-2")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"url": "https://other.com", "alias": "taken"}
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldListUserUrls() throws Exception {
        // Create two URLs for user-1
        mockMvc.perform(post("/api/v1/urls")
                        .with(jwt().jwt(j -> j.subject("user-1")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"url": "https://example1.com"}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/urls")
                        .with(jwt().jwt(j -> j.subject("user-1")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"url": "https://example2.com"}
                                """))
                .andExpect(status().isCreated());

        // List URLs for user-1
        mockMvc.perform(get("/api/v1/urls").with(jwt().jwt(j -> j.subject("user-1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(2));
    }

    @Test
    void shouldDeleteOwnUrl() throws Exception {
        // Create a URL
        mockMvc.perform(post("/api/v1/urls")
                        .with(jwt().jwt(j -> j.subject("user-1")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"url": "https://example.com", "alias": "del-me"}
                                """))
                .andExpect(status().isCreated());

        // Delete it
        mockMvc.perform(delete("/api/v1/urls/del-me").with(jwt().jwt(j -> j.subject("user-1"))))
                .andExpect(status().isNoContent());

        // Verify it's gone
        mockMvc.perform(get("/del-me")).andExpect(status().isNotFound());
    }

    @Test
    void shouldForbidDeletingOtherUsersUrl() throws Exception {
        // user-1 creates a URL
        mockMvc.perform(post("/api/v1/urls")
                        .with(jwt().jwt(j -> j.subject("user-1")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"url": "https://example.com", "alias": "not-yours"}
                                """))
                .andExpect(status().isCreated());

        // user-2 tries to delete it
        mockMvc.perform(delete("/api/v1/urls/not-yours").with(jwt().jwt(j -> j.subject("user-2"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldReturn404ForNonExistentShortcode() throws Exception {
        mockMvc.perform(get("/does-not-exist")).andExpect(status().isNotFound());
    }
}
