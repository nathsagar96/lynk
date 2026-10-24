package ly.lynk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.util.UUID;
import ly.lynk.model.UrlMapping;
import ly.lynk.repository.UrlMappingRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestSecurityConfiguration.class})
class UrlShortenerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UrlMappingRepository repository;

    @Test
    @DisplayName("a created link can be resolved, tracked, inspected and deleted by its owner")
    void endToEndLifecycle() throws Exception {
        String userA = UUID.randomUUID().toString();
        String userB = UUID.randomUUID().toString();

        // 1. Unauthenticated creation is rejected with 401
        mockMvc.perform(post("/api/v1/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"originalUrl":"https://spring.io/projects/spring-boot","customAlias":"spring"}"""))
                .andExpect(status().isUnauthorized());

        // 2. Authenticated creation as userA succeeds
        mockMvc.perform(post("/api/v1/urls")
                        .with(jwt().jwt(b -> b.subject(userA)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"originalUrl":"https://spring.io/projects/spring-boot","customAlias":"spring"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.alias").value("spring"))
                .andExpect(jsonPath("$.originalUrl").value("https://spring.io/projects/spring-boot"))
                .andExpect(jsonPath("$.ownerId").value(userA));

        // 3. Public metadata inspection succeeds and omits ownerId for privacy
        mockMvc.perform(get("/api/v1/urls/spring"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alias").value("spring"))
                .andExpect(jsonPath("$.ownerId").doesNotExist())
                .andExpect(jsonPath("$.clickCount").doesNotExist());

        // 4. userA lists their owned URLs
        mockMvc.perform(get("/api/v1/urls").with(jwt().jwt(b -> b.subject(userA))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].alias").value("spring"))
                .andExpect(jsonPath("$.content[0].ownerId").value(userA));

        // 5. Public HEAD and GET redirects work without authentication
        mockMvc.perform(head("/spring")).andExpect(status().isFound());

        mockMvc.perform(get("/spring").queryParam("source", "test"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://spring.io/projects/spring-boot?source=test"));

        // 6. Non-owner (userB) querying stats is forbidden
        mockMvc.perform(get("/api/v1/urls/spring/stats").with(jwt().jwt(b -> b.subject(userB))))
                .andExpect(status().isForbidden());

        // 7. Owner (userA) querying stats succeeds
        mockMvc.perform(get("/api/v1/urls/spring/stats").with(jwt().jwt(b -> b.subject(userA))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clickCount").value(1))
                .andExpect(jsonPath("$.ownerId").value(userA));

        // 8. Non-owner (userB) deleting is forbidden
        mockMvc.perform(delete("/api/v1/urls/spring").with(jwt().jwt(b -> b.subject(userB))))
                .andExpect(status().isForbidden());

        // 9. Owner (userA) deletes the URL
        mockMvc.perform(delete("/api/v1/urls/spring").with(jwt().jwt(b -> b.subject(userA))))
                .andExpect(status().isNoContent());

        // 10. Alias is now gone
        mockMvc.perform(get("/api/v1/urls/spring")).andExpect(status().isNotFound());
        mockMvc.perform(get("/spring")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("a generated alias resolves to the original URL")
    void resolvesGeneratedAlias() throws Exception {
        String user = UUID.randomUUID().toString();

        MvcResult result = mockMvc.perform(post("/api/v1/urls")
                        .with(jwt().jwt(b -> b.subject(user)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"originalUrl":"https://example.org/page"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.originalUrl").value("https://example.org/page"))
                .andExpect(jsonPath("$.alias").isNotEmpty())
                .andExpect(jsonPath("$.ownerId").value(user))
                .andReturn();

        String alias = JsonPath.read(result.getResponse().getContentAsString(), "$.alias");
        mockMvc.perform(get("/" + alias))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.org/page"));
    }

    @Test
    @DisplayName("an expiry timestamp in the past is rejected")
    void rejectsPastExpiry() throws Exception {
        String user = UUID.randomUUID().toString();

        mockMvc.perform(post("/api/v1/urls")
                        .with(jwt().jwt(b -> b.subject(user)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"originalUrl":"https://example.org/gone","customAlias":"gone",
                                 "expiresAt":"2020-01-01T00:00:00Z"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.expiresAt").exists());
    }

    @Test
    @DisplayName("an expired link can no longer be resolved but its details and stats remain available to owner")
    void expiredLinkIsNotResolvable() throws Exception {
        String user = UUID.randomUUID().toString();

        repository.saveAndFlush(new UrlMapping(
                "https://example.org/gone",
                "gone",
                Instant.parse("2020-01-01T00:00:00Z"),
                Instant.parse("2020-01-02T00:00:00Z"),
                user));

        mockMvc.perform(get("/gone"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.title").exists());

        // Public metadata remains accessible
        mockMvc.perform(get("/api/v1/urls/gone")).andExpect(status().isOk());

        // Stats accessible to owner
        mockMvc.perform(get("/api/v1/urls/gone/stats").with(jwt().jwt(b -> b.subject(user))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ownerId").value(user));
    }

    @Test
    @DisplayName("a created link can have its QR code fetched as a valid PNG image")
    void retrievesQrCodeForCreatedLink() throws Exception {
        String user = UUID.randomUUID().toString();

        mockMvc.perform(post("/api/v1/urls")
                        .with(jwt().jwt(b -> b.subject(user)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"originalUrl":"https://spring.io/projects/spring-boot","customAlias":"qrlink"}"""))
                .andExpect(status().isCreated());

        MvcResult qrResult = mockMvc.perform(get("/api/v1/urls/qrlink/qr").param("size", "300"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", MediaType.IMAGE_PNG_VALUE))
                .andExpect(header().string("Content-Disposition", "inline"))
                .andExpect(header().exists("Cache-Control"))
                .andReturn();

        byte[] bytes = qrResult.getResponse().getContentAsByteArray();
        assertThat(bytes).isNotEmpty();
        assertThat(bytes[0]).isEqualTo((byte) 0x89);
        assertThat(bytes[1]).isEqualTo((byte) 0x50);
        assertThat(bytes[2]).isEqualTo((byte) 0x4E);
        assertThat(bytes[3]).isEqualTo((byte) 0x47);
    }
}
