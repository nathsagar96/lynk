package com.lynk.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * One page of the caller's own links, for {@code GET /api/v1/url/links}.
 *
 * @param content       the links on this page, newest first
 * @param totalElements how many links the caller owns in total, across every page
 */
public record LinkPageResponse(
        @Schema(
                description = "The links on this page, newest first. Each entry is the same representation "
                        + "that the stats endpoint returns for a single code.")
        List<UrlStatsResponse> content,

        @Schema(
                description = "How many links the caller owns in total, across every page. Walk `page` until "
                        + "you have seen this many.",
                example = "57")
        long totalElements) {}
