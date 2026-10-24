package ly.lynk;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
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
@Import(TestcontainersConfiguration.class)
class UrlShortenerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UrlMappingRepository repository;

    @Test
    @DisplayName("a created link can be resolved, tracked, inspected and deleted")
    void endToEndLifecycle() throws Exception {
        mockMvc.perform(post("/api/v1/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"originalUrl":"https://spring.io/projects/spring-boot","customAlias":"spring"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.alias").value("spring"))
                .andExpect(jsonPath("$.originalUrl").value("https://spring.io/projects/spring-boot"));

        mockMvc.perform(get("/api/v1/urls/spring"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alias").value("spring"))
                .andExpect(jsonPath("$.clickCount").doesNotExist());

        mockMvc.perform(head("/spring")).andExpect(status().isFound());

        mockMvc.perform(get("/api/v1/urls/spring/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clickCount").value(0));

        mockMvc.perform(get("/spring").queryParam("source", "test"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://spring.io/projects/spring-boot?source=test"));

        mockMvc.perform(get("/api/v1/urls/spring/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clickCount").value(1));

        mockMvc.perform(delete("/api/v1/urls/spring")).andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/urls/spring")).andExpect(status().isNotFound());
        mockMvc.perform(get("/spring")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("a generated alias resolves to the original URL")
    void resolvesGeneratedAlias() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"originalUrl":"https://example.org/page"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.originalUrl").value("https://example.org/page"))
                .andExpect(jsonPath("$.alias").isNotEmpty())
                .andReturn();

        String alias = JsonPath.read(result.getResponse().getContentAsString(), "$.alias");
        mockMvc.perform(get("/" + alias))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.org/page"));
    }

    @Test
    @DisplayName("an expiry timestamp in the past is rejected")
    void rejectsPastExpiry() throws Exception {
        mockMvc.perform(post("/api/v1/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"originalUrl":"https://example.org/gone","customAlias":"gone",
                                 "expiresAt":"2020-01-01T00:00:00Z"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.expiresAt").exists());
    }

    @Test
    @DisplayName("an expired link can no longer be resolved but its details remain available")
    void expiredLinkIsNotResolvable() throws Exception {
        repository.saveAndFlush(new UrlMapping(
                "https://example.org/gone",
                "gone",
                Instant.parse("2020-01-01T00:00:00Z"),
                Instant.parse("2020-01-02T00:00:00Z")));

        mockMvc.perform(get("/gone"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.title").exists());

        mockMvc.perform(get("/api/v1/urls/gone")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/urls/gone/stats")).andExpect(status().isOk());
    }
}
