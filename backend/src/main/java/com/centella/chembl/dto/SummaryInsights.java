package com.centella.chembl.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Aggregated summary insights computed from the full activity result set.
 *
 * Assumptions:
 * - "Unique compounds" = distinct molecule_chembl_id values.
 * - "Most common activity type" = standard_type with highest frequency.
 * - "Top 5 strongest compounds" = records with the lowest numeric standard_value
 *   among those that have a valid numeric value. Where multiple records exist
 *   for the same compound, the best (lowest) value is used.
 * - Records with null/non-numeric standard_value are excluded from ranking.
 */
@Data
@Builder
public class SummaryInsights {
    private int totalRecords;
    private int uniqueCompounds;
    private String mostCommonActivityType;
    private List<TopCompound> top5Compounds;
}
