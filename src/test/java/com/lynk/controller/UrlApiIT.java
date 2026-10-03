package com.lynk.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.lynk.AbstractIntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@AutoConfigureMockMvc
@DisplayName("URL shortener HTTP API")
class UrlApiIT extends AbstractIntegrationTestBase {

    private static final String DESTINATION = "https://example.com/landing?ref=lynk";

    @Autowired
    private MockMvc mockMvc;

    private ResultActions shortenWith(String json) throws Exception {
        return mockMvc.perform(post("/api/v1/url/shorten")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private void shortenWithAlias(String alias) throws Exception {
        shortenWith("{\"url\":\"" + DESTINATION + "\",\"customAlias\":\"" + alias + "\"}")
                .andExpect(status().isCreated());
    }

    @Nested
    @DisplayName("POST /api/v1/url/shorten")
    class Shorten {

        @Test
        @DisplayName("returns 201 with an absolute short URL")
        void shorten_returnsCreatedWithAbsoluteShortUrl_whenUrlIsValid() throws Exception {
            // Arrange & Act & Assert
            shortenWith("{\"url\":\"" + DESTINATION + "\"}")
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.shortUrl").value(containsString("http://localhost:8080/")))
                    .andExpect(jsonPath("$.shortCode").isNotEmpty())
                    .andExpect(jsonPath("$.originalUrl").value(DESTINATION))
                    .andExpect(jsonPath("$.expiresAt").doesNotExist());
        }

        @Test
        @DisplayName("returns an expiry when hoursToExpire is supplied")
        void shorten_returnsExpiry_whenHoursToExpireSupplied() throws Exception {
            // Arrange & Act & Assert
            shortenWith("{\"url\":\"" + DESTINATION + "\",\"hoursToExpire\":48}")
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.expiresAt").isNotEmpty());
        }

        @Test
        @DisplayName("returns a Location header naming the new short URL")
        void shorten_returnsLocationHeader_whenUrlIsValid() throws Exception {
            // Arrange & Act
            var response = shortenWith("{\"url\":\"" + DESTINATION + "\"}")
                    .andExpect(status().isCreated())
                    .andReturn()
                    .getResponse();

            // Assert
            String shortUrl = JsonPath.read(response.getContentAsString(), "$.shortUrl");
            assertThat(shortUrl).startsWith("http://localhost:8080/");
            assertThat(response.getHeader("Location")).isEqualTo(shortUrl);
        }

        @Test
        @DisplayName("returns 409 when the custom alias is already taken")
        void shorten_returnsConflict_whenAliasIsAlreadyTaken() throws Exception {
            // Arrange
            shortenWithAlias("dup-api-alias");

            // Act & Assert
            shortenWith("{\"url\":\"" + DESTINATION + "\",\"customAlias\":\"dup-api-alias\"}")
                    .andExpect(status().isConflict())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.type").value("/problems/alias-conflict"));
        }

        @Test
        @DisplayName("returns 400 with a validation problem when the URL is blank")
        void shorten_returnsValidationProblem_whenUrlIsBlank() throws Exception {
            // Arrange & Act & Assert
            shortenWith("{\"url\":\"\"}")
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.type").value("/problems/validation-failed"))
                    .andExpect(jsonPath("$.errors[0].field").value("url"));
        }

        @Test
        @DisplayName("returns 400 with an invalid-url problem when the URL is relative")
        void shorten_returnsInvalidUrlProblem_whenUrlIsRelative() throws Exception {
            // Arrange & Act & Assert
            shortenWith("{\"url\":\"/nope\"}")
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.type").value("/problems/invalid-url"));
        }

        @ParameterizedTest(name = "reserved alias \"{0}\" is rejected")
        @DisplayName("returns 400 for a reserved alias")
        @ValueSource(strings = {"error", "api", "actuator", "health"})
        void shorten_returnsReservedAliasProblem_whenAliasIsReserved(String alias) throws Exception {
            // Arrange & Act & Assert
            shortenWith("{\"url\":\"" + DESTINATION + "\",\"customAlias\":\"" + alias + "\"}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.type").value("/problems/reserved-alias"));
        }
    }

    @Nested
    @DisplayName("GET /{shortCode}")
    class Redirect {

        @Test
        @DisplayName("returns 302 with a Location header and no-store")
        void redirect_returnsFoundWithLocation_whenCodeExists() throws Exception {
            // Arrange
            shortenWithAlias("redirect-alias");

            // Act & Assert
            mockMvc.perform(get("/redirect-alias"))
                    .andExpect(status().isFound())
                    .andExpect(header().string("Location", DESTINATION))
                    .andExpect(header().string("Cache-Control", containsString("no-store")));
        }

        @Test
        @DisplayName("increments the click count on each hit")
        void redirect_incrementsClickCount_whenCalledRepeatedly() throws Exception {
            // Arrange
            shortenWithAlias("count-alias");

            // Act
            mockMvc.perform(get("/count-alias")).andExpect(status().isFound());
            mockMvc.perform(get("/count-alias")).andExpect(status().isFound());

            // Assert
            mockMvc.perform(get("/api/v1/url/stats/count-alias"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.clickCount").value(2));
        }

        @Test
        @DisplayName("returns 404 problem for an unknown but well-formed code")
        void redirect_returnsNotFoundProblem_whenCodeIsUnknown() throws Exception {
            // Arrange & Act & Assert
            mockMvc.perform(get("/nosuchcode"))
                    .andExpect(status().isNotFound())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.type").value("/problems/url-not-found"))
                    .andExpect(jsonPath("$.instance").value("/nosuchcode"));
        }

        @ParameterizedTest(name = "\"{0}\" is not treated as a short URL")
        @DisplayName("does not report a missing short URL for paths outside the code pattern")
        // Below the 3-character bound, containing a character outside the alphabet, and above the
        // 32-character bound respectively.
        @ValueSource(strings = {"/ab", "/favicon.ico", "/waytoolongtobeavalidshortcodeatallhere"})
        void redirect_doesNotReportMissingShortUrl_whenPathIsNotAValidCodePattern(String path) throws Exception {
            // Arrange & Act
            var response = mockMvc.perform(get(path)).andReturn().getResponse();

            // Assert
            assertThat(response.getStatus()).isEqualTo(404);
            assertThat(response.getContentAsString()).doesNotContain("/problems/url-not-found");
        }
    }

    @Nested
    @DisplayName("GET /api/v1/url/stats/{shortCode}")
    class Stats {

        @Test
        @DisplayName("returns the stored mapping")
        void stats_returnsStoredMapping_whenCodeExists() throws Exception {
            // Arrange
            shortenWithAlias("stats-api-alias");

            // Act & Assert
            mockMvc.perform(get("/api/v1/url/stats/stats-api-alias"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.originalUrl").value(DESTINATION))
                    .andExpect(jsonPath("$.shortUrl").value("http://localhost:8080/stats-api-alias"))
                    .andExpect(jsonPath("$.clickCount").value(0))
                    .andExpect(jsonPath("$.createdAt").isNotEmpty());
        }

        @Test
        @DisplayName("returns 404 for an unknown code")
        void stats_returnsNotFoundProblem_whenCodeIsUnknown() throws Exception {
            // Arrange & Act & Assert
            mockMvc.perform(get("/api/v1/url/stats/nosuchcode"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.type").value("/problems/url-not-found"));
        }
    }
}
