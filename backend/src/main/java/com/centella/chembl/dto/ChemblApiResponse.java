package com.centella.chembl.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Raw deserialization model for the ChEMBL REST API response.
 *
 * ChEMBL response structure:
 * {
 *   "activities": [...],
 *   "page_meta": {
 *     "total_count": 1234,
 *     "limit": 100,
 *     "offset": 0,
 *     "next": "...",
 *     "previous": null
 *   }
 * }
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChemblApiResponse {

    @JsonProperty("activities")
    private List<ActivityRecord> activities;

    @JsonProperty("page_meta")
    private PageMeta pageMeta;

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PageMeta {

        @JsonProperty("total_count")
        private Integer totalCount;

        @JsonProperty("limit")
        private Integer limit;

        @JsonProperty("offset")
        private Integer offset;

        @JsonProperty("next")
        private String next;

        @JsonProperty("previous")
        private String previous;
    }
}
