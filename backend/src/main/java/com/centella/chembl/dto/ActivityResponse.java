package com.centella.chembl.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Top-level response envelope returned by the backend API.
 * Wraps filtered activity records, pagination info, and summary insights.
 */
@Data
@Builder
public class ActivityResponse {

    /** "success" or "error" */
    private String status;

    private String targetId;

    /** Total records matching applied filters (across all pages) */
    private int totalRecords;

    /** Current page (0-indexed) */
    private int page;

    /** Number of records per page */
    private int pageSize;

    /** Total pages available */
    private int totalPages;

    /** Activity records for the current page */
    private List<ActivityRecord> activities;

    /** Aggregated summary — computed from ALL matching records, not just current page */
    private SummaryInsights summary;

    /** Applied filters for transparency */
    private AppliedFilters appliedFilters;

    @Data
    @Builder
    public static class AppliedFilters {
        private String activityType;
        private String assayType;
        private String organism;
        private String standardUnits;
        private String relation;
    }
}
