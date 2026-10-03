package com.lynk;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import dasniko.testcontainers.keycloak.KeycloakContainer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.Keycloak;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * The one test that talks to a real Keycloak: the imported realm mints the token, the app's
 * resource-server config discovers the realm's keys from its issuer and validates the token. This is
 * what proves the wiring end to end — the rest of the suite forges tokens and stays offline.
 * <p>
 * Deliberately not extending {@link AbstractIntegrationTestBase}: that base supplies a mocked
 * {@link org.springframework.security.oauth2.jwt.JwtDecoder}, and this test needs the real one built
 * from the container's issuer.
 */
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "lynk.base-url=http://localhost:8080")
@Testcontainers
class KeycloakAuthenticationIT {

    private static final String REALM = "lynk";
    private static final String CLIENT_ID = "lynk-cli";
    private static final String USERNAME = "lynk";
    private static final String PASSWORD = "lynk";

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"));

    @Container
    static final KeycloakContainer KEYCLOAK =
            new KeycloakContainer("quay.io/keycloak/keycloak:26.8").withRealmImportFile("/keycloak/lynk-realm.json");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add(
                "spring.security.oauth2.resourceserver.jwt.issuer-uri",
                () -> KEYCLOAK.getAuthServerUrl() + "/realms/" + REALM);
    }

    private static String fetchAccessToken() {
        try (Keycloak keycloak =
                Keycloak.getInstance(KEYCLOAK.getAuthServerUrl(), REALM, USERNAME, PASSWORD, CLIENT_ID)) {
            return keycloak.tokenManager().getAccessTokenString();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void aRealKeycloakToken_opensTheApi_andOwnsWhatItCreates() throws Exception {
        String token = fetchAccessToken();

        MvcResult created = mockMvc.perform(post("/api/v1/url/shorten")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://example.com/keycloak\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.originalUrl").value("https://example.com/keycloak"))
                .andReturn();

        // The real Keycloak subject is what got stored as the owner, so reading the stats back with
        // the same token proves ownership was persisted from the live token and not just from a stub.
        String shortCode = JsonPath.read(created.getResponse().getContentAsString(), "$.shortCode");

        mockMvc.perform(get("/api/v1/url/stats/" + shortCode).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.originalUrl").value("https://example.com/keycloak"));
    }

    @Test
    void withoutAToken_theApiIsStillLocked() throws Exception {
        mockMvc.perform(get("/api/v1/url/stats/whatever"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value("/problems/unauthenticated"));
    }
}
