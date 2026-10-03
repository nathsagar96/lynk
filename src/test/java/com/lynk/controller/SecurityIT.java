package com.lynk.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lynk.AbstractIntegrationTestBase;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The authorization rules, exercised with a forged token so no Keycloak is needed. That a real
 * Keycloak token is accepted is {@code KeycloakAuthenticationIT}'s job.
 */
@AutoConfigureMockMvc
class SecurityIT extends AbstractIntegrationTestBase {

    private static final String DESTINATION = "https://example.com/secure";

    @Autowired
    private MockMvc mockMvc;

    @Nested
    class ProtectedApi {

        @Test
        void shorten_isRejected_whenNoTokenIsSent() throws Exception {
            mockMvc.perform(post("/api/v1/url/shorten")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"url\":\"" + DESTINATION + "\"}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.type").value("/problems/unauthenticated"))
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(header().string("WWW-Authenticate", "Bearer"));
        }

        @Test
        void shorten_createsTheLink_whenATokenIsSent() throws Exception {
            mockMvc.perform(post("/api/v1/url/shorten")
                            .with(jwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"url\":\"" + DESTINATION + "\"}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.originalUrl").value(DESTINATION));
        }

        @Test
        void stats_isRejected_whenNoTokenIsSent() throws Exception {
            mockMvc.perform(get("/api/v1/url/stats/whatever"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.type").value("/problems/unauthenticated"));
        }

        @Test
        void stats_returnsTheMapping_whenATokenIsSent() throws Exception {
            mockMvc.perform(post("/api/v1/url/shorten")
                            .with(jwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"url\":\"" + DESTINATION + "\",\"customAlias\":\"secured-link\"}"))
                    .andExpect(status().isCreated());

            mockMvc.perform(get("/api/v1/url/stats/secured-link").with(jwt()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.originalUrl").value(DESTINATION));
        }

        @Test
        void stats_stillReportsAMissingCode_whenTokenIsValid() throws Exception {
            mockMvc.perform(get("/api/v1/url/stats/nosuchcode").with(jwt()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.type").value("/problems/url-not-found"));
        }

        @Test
        void stats_isNotFound_whenTheLinkBelongsToAnotherSubject() throws Exception {
            mockMvc.perform(post("/api/v1/url/shorten")
                            .with(jwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"url\":\"" + DESTINATION + "\",\"customAlias\":\"owned-link\"}"))
                    .andExpect(status().isCreated());

            mockMvc.perform(get("/api/v1/url/stats/owned-link").with(jwt().jwt(j -> j.subject("someone-else"))))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.type").value("/problems/url-not-found"));
        }

        @Test
        void shorten_isRejected_whenTheTokenCarriesNoSubject() throws Exception {
            // The link would be unowned and therefore unreadable by anybody, including its creator.
            mockMvc.perform(post("/api/v1/url/shorten")
                            .with(jwt().jwt(j -> j.claims(c -> c.remove("sub"))))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"url\":\"" + DESTINATION + "\"}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.type").value("/problems/unauthenticated"));
        }

        @Test
        void links_omitsLinksOwnedByAnotherSubject() throws Exception {
            mockMvc.perform(post("/api/v1/url/shorten")
                            .with(jwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"url\":\"" + DESTINATION + "\",\"customAlias\":\"mine-link\"}"))
                    .andExpect(status().isCreated());

            mockMvc.perform(get("/api/v1/url/links").with(jwt().jwt(j -> j.subject("someone-else"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(0))
                    .andExpect(jsonPath("$.content").isEmpty());
        }

        @Test
        void delete_isNotFound_whenTheLinkBelongsToAnotherSubject() throws Exception {
            mockMvc.perform(post("/api/v1/url/shorten")
                            .with(jwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"url\":\"" + DESTINATION + "\",\"customAlias\":\"their-link\"}"))
                    .andExpect(status().isCreated());

            mockMvc.perform(delete("/api/v1/url/links/their-link").with(jwt().jwt(j -> j.subject("someone-else"))))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.type").value("/problems/url-not-found"));
        }
    }

    @Nested
    class PublicRoutes {

        @Test
        void redirect_staysOpen_withoutAToken() throws Exception {
            mockMvc.perform(post("/api/v1/url/shorten")
                            .with(jwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"url\":\"" + DESTINATION + "\",\"customAlias\":\"open-link\"}"))
                    .andExpect(status().isCreated());

            mockMvc.perform(get("/open-link"))
                    .andExpect(status().isFound())
                    .andExpect(header().string("Location", DESTINATION));
        }

        @Test
        void health_staysOpen_withoutAToken() throws Exception {
            mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
        }

        @Test
        void openApiDocument_staysOpen_andPublishesTheBearerScheme() throws Exception {
            mockMvc.perform(get("/v3/api-docs"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme")
                            .value("bearer"))
                    .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.bearerFormat")
                            .value("JWT"))
                    // Declared once on the controller, so every operation still has to carry it.
                    .andExpect(jsonPath("$.paths['/api/v1/url/links'].get.security[0].bearerAuth")
                            .isEmpty())
                    .andExpect(jsonPath("$.paths['/api/v1/url/links/{shortCode}'].delete.security[0].bearerAuth")
                            .isEmpty());
        }
    }
}
