package com.lynk.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.lynk.AbstractIntegrationTestBase;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@AutoConfigureMockMvc
class UrlApiIT extends AbstractIntegrationTestBase {

    private static final String DESTINATION = "https://example.com/landing?ref=lynk";

    @Autowired
    private MockMvc mockMvc;

    private ResultActions shortenWith(String json) throws Exception {
        return mockMvc.perform(post("/api/v1/url/shorten")
                .with(jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private void shortenWithAlias(String alias) throws Exception {
        shortenWith("{\"url\":\"" + DESTINATION + "\",\"customAlias\":\"" + alias + "\"}")
                .andExpect(status().isCreated());
    }

    @Nested
    class Shorten {

        @Test
        void shorten_returnsCreatedWithAbsoluteShortUrl_whenUrlIsValid() throws Exception {
            shortenWith("{\"url\":\"" + DESTINATION + "\"}")
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.shortUrl").value(containsString("http://localhost:8080/")))
                    .andExpect(jsonPath("$.shortCode").isNotEmpty())
                    .andExpect(jsonPath("$.originalUrl").value(DESTINATION))
                    .andExpect(jsonPath("$.expiresAt").doesNotExist());
        }

        @Test
        void shorten_returnsExpiry_whenHoursToExpireSupplied() throws Exception {
            shortenWith("{\"url\":\"" + DESTINATION + "\",\"hoursToExpire\":48}")
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.expiresAt").isNotEmpty());
        }

        @Test
        void shorten_returnsLocationHeader_whenUrlIsValid() throws Exception {
            var response = shortenWith("{\"url\":\"" + DESTINATION + "\"}")
                    .andExpect(status().isCreated())
                    .andReturn()
                    .getResponse();

            String shortUrl = JsonPath.read(response.getContentAsString(), "$.shortUrl");
            assertThat(shortUrl).startsWith("http://localhost:8080/");
            assertThat(response.getHeader("Location")).isEqualTo(shortUrl);
        }

        @Test
        void shorten_returnsConflict_whenAliasIsAlreadyTaken() throws Exception {
            shortenWithAlias("dup-api-alias");

            shortenWith("{\"url\":\"" + DESTINATION + "\",\"customAlias\":\"dup-api-alias\"}")
                    .andExpect(status().isConflict())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.type").value("/problems/alias-conflict"));
        }

        @Test
        void shorten_returnsValidationProblem_whenUrlIsBlank() throws Exception {
            shortenWith("{\"url\":\"\"}")
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.type").value("/problems/validation-failed"))
                    .andExpect(jsonPath("$.errors[0].field").value("url"));
        }

        @Test
        void shorten_returnsInvalidUrlProblem_whenUrlIsRelative() throws Exception {
            shortenWith("{\"url\":\"/nope\"}")
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.type").value("/problems/invalid-url"));
        }

        @ParameterizedTest(name = "reserved alias \"{0}\" is rejected")
        @ValueSource(strings = {"error", "api", "actuator", "health"})
        void shorten_returnsReservedAliasProblem_whenAliasIsReserved(String alias) throws Exception {
            shortenWith("{\"url\":\"" + DESTINATION + "\",\"customAlias\":\"" + alias + "\"}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.type").value("/problems/reserved-alias"));
        }
    }

    @Nested
    class Redirect {

        @Test
        void redirect_returnsFoundWithLocation_whenCodeExists() throws Exception {
            shortenWithAlias("redirect-alias");

            mockMvc.perform(get("/redirect-alias"))
                    .andExpect(status().isFound())
                    .andExpect(header().string("Location", DESTINATION))
                    .andExpect(header().string("Cache-Control", containsString("no-store")));
        }

        @Test
        void redirect_incrementsClickCount_whenCalledRepeatedly() throws Exception {
            shortenWithAlias("count-alias");

            mockMvc.perform(get("/count-alias")).andExpect(status().isFound());
            mockMvc.perform(get("/count-alias")).andExpect(status().isFound());

            mockMvc.perform(get("/api/v1/url/stats/count-alias").with(jwt()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.clickCount").value(2));
        }

        @Test
        void redirect_returnsNotFoundProblem_whenCodeIsUnknown() throws Exception {
            mockMvc.perform(get("/nosuchcode"))
                    .andExpect(status().isNotFound())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.type").value("/problems/url-not-found"))
                    .andExpect(jsonPath("$.instance").value("/nosuchcode"));
        }

        @ParameterizedTest(name = "\"{0}\" is not treated as a short URL")
        // Below the 3-character bound, containing a character outside the alphabet, and above the
        // 32-character bound respectively.
        @ValueSource(strings = {"/ab", "/favicon.ico", "/waytoolongtobeavalidshortcodeatallhere"})
        void redirect_doesNotReportMissingShortUrl_whenPathIsNotAValidCodePattern(String path) throws Exception {
            var response = mockMvc.perform(get(path)).andReturn().getResponse();

            assertThat(response.getStatus()).isEqualTo(404);
            assertThat(response.getContentAsString()).doesNotContain("/problems/url-not-found");
        }
    }

    @Nested
    class Stats {

        @Test
        void stats_returnsStoredMapping_whenCodeExists() throws Exception {
            shortenWithAlias("stats-api-alias");

            mockMvc.perform(get("/api/v1/url/stats/stats-api-alias").with(jwt()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.originalUrl").value(DESTINATION))
                    .andExpect(jsonPath("$.shortUrl").value("http://localhost:8080/stats-api-alias"))
                    .andExpect(jsonPath("$.clickCount").value(0))
                    .andExpect(jsonPath("$.createdAt").isNotEmpty());
        }

        @Test
        void stats_returnsNotFoundProblem_whenCodeIsUnknown() throws Exception {
            mockMvc.perform(get("/api/v1/url/stats/nosuchcode").with(jwt()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.type").value("/problems/url-not-found"));
        }
    }

    @Nested
    class ListLinks {

        @Test
        void links_returnsAPageOfTheCallersOwnLinks() throws Exception {
            shortenWithAlias("listed-one");
            shortenWithAlias("listed-two");

            mockMvc.perform(get("/api/v1/url/links").with(jwt()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(2))
                    .andExpect(jsonPath("$.content.length()").value(2));
        }

        @Test
        void links_honoursSizeAndReportsTheFullTotal() throws Exception {
            shortenWithAlias("paged-one");
            shortenWithAlias("paged-two");
            shortenWithAlias("paged-three");

            mockMvc.perform(get("/api/v1/url/links").param("size", "2").with(jwt()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(2))
                    .andExpect(jsonPath("$.totalElements").value(3));
        }

        @Test
        void links_isRejected_whenNoTokenIsSent() throws Exception {
            mockMvc.perform(get("/api/v1/url/links"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.type").value("/problems/unauthenticated"));
        }

        @ParameterizedTest(name = "{0} is rejected")
        @CsvSource({"page, -1", "size, 0", "size, 101"})
        void links_returnsValidationProblem_whenPagingIsOutOfRange(String param, String value) throws Exception {
            mockMvc.perform(get("/api/v1/url/links").param(param, value).with(jwt()))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.type").value("/problems/validation-failed"))
                    .andExpect(jsonPath("$.errors[0].field").value(param));
        }
    }

    @Nested
    class DeleteLink {

        @Test
        void delete_returnsNoContent_andStopsTheLinkResolving() throws Exception {
            shortenWithAlias("deletable-link");

            mockMvc.perform(delete("/api/v1/url/links/deletable-link").with(jwt()))
                    .andExpect(status().isNoContent())
                    .andExpect(content().string(""));

            mockMvc.perform(get("/api/v1/url/stats/deletable-link").with(jwt()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.type").value("/problems/url-not-found"));

            mockMvc.perform(get("/deletable-link"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.type").value("/problems/url-not-found"));
        }

        @Test
        void delete_isRejected_whenNoTokenIsSent() throws Exception {
            shortenWithAlias("protected-link");

            mockMvc.perform(delete("/api/v1/url/links/protected-link"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.type").value("/problems/unauthenticated"));
        }

        @Test
        void delete_returnsNotFoundProblem_whenTheCodeIsUnknown() throws Exception {
            mockMvc.perform(delete("/api/v1/url/links/nosuchcode").with(jwt()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.type").value("/problems/url-not-found"));
        }
    }
}
