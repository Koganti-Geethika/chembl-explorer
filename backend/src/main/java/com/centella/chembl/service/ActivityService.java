package com.centella.chembl.service;

import com.centella.chembl.client.ChemblApiClient;
import com.centella.chembl.dto.*;
import com.centella.chembl.exception.InvalidTargetIdException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service layer — orchestrates ChEMBL API calls, filtering, pagination, and analytics.
 *
 * Filtering approach: HYBRID
 * - Server-side: activityType, assayType, organism, units, relation are passed directly
 *   to the ChEMBL API query params to minimize data transfer.
 * - Client-side: additional in-memory filtering can be layered on top if needed,
 *   and summary computation is always done in-memory after fetch.
 *
 * This avoids fetching all records for complex filter combinations while still
 * supporting dynamic post-fetch operations like summary analytics.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ActivityService {

    private static final int DEFAULT_PAGE_SIZE = 25;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int TOP_COMPOUNDS_COUNT = 5;

    private final ChemblApiClient chemblApiClient;

    /**
     * Main method: fetch, filter, paginate, and summarize activity data.
     *
     * @param targetId     ChEMBL Target ID — validated before use
     * @param activityType Optional filter: IC50, Ki, EC50, etc.
     * @param assayType    Optional filter: B (binding), F (functional), etc.
     * @param organism     Optional filter: e.g. "Homo sapiens"
     * @param units        Optional filter: e.g. "nM"
     * @param relation     Optional filter: e.g. "="
     * @param page         0-indexed page number
     * @param pageSize     Records per page (capped at MAX_PAGE_SIZE)
     * @return Wrapped ActivityResponse with records, pagination, and summary
     */
    public ActivityResponse getActivities(
            String targetId,
            String activityType,
            String assayType,
            String organism,
            String units,
            String relation,
            int page,
            int pageSize) {

        // Validate target ID format
        validateTargetId(targetId);

        // Clamp page size
        pageSize = Math.min(Math.max(1, pageSize), MAX_PAGE_SIZE);
        page = Math.max(0, page);

        // Normalize filter values (trim + uppercase type for consistency)
        String normalizedType = normalize(activityType);
        String normalizedAssayType = normalize(assayType);
        String normalizedOrganism = normalize(organism);
        String normalizedUnits = normalize(units);
        String normalizedRelation = normalize(relation);

        // Fetch all records (server-side filtered + cached)
        List<ActivityRecord> allRecords = chemblApiClient.fetchAllActivities(
                targetId.toUpperCase(),
                normalizedType,
                normalizedAssayType,
                normalizedOrganism,
                normalizedUnits,
                normalizedRelation
        );

        // Compute summary on full result set before pagination
        SummaryInsights summary = computeSummary(allRecords);

        // Paginate
        int totalRecords = allRecords.size();
        int totalPages = (int) Math.ceil((double) totalRecords / pageSize);
        int fromIndex = Math.min(page * pageSize, totalRecords);
        int toIndex = Math.min(fromIndex + pageSize, totalRecords);

        List<ActivityRecord> pageRecords = allRecords.subList(fromIndex, toIndex);

        return ActivityResponse.builder()
                .status("success")
                .targetId(targetId.toUpperCase())
                .totalRecords(totalRecords)
                .page(page)
                .pageSize(pageSize)
                .totalPages(totalPages)
                .activities(pageRecords)
                .summary(summary)
                .appliedFilters(ActivityResponse.AppliedFilters.builder()
                        .activityType(normalizedType)
                        .assayType(normalizedAssayType)
                        .organism(normalizedOrganism)
                        .standardUnits(normalizedUnits)
                        .relation(normalizedRelation)
                        .build())
                .build();
    }

    /**
     * Computes summary insights across all (unfiltered by page) activity records.
     *
     * Assumptions documented:
     * 1. Unique compounds = distinct molecule_chembl_id values (null IDs excluded).
     * 2. Most common activity type = standard_type with highest frequency (null types excluded).
     * 3. Top 5 compounds: for each unique compound, take their best (lowest) numeric standard_value.
     *    Compounds with no valid numeric value are excluded.
     *    Ranking is by lowest value (lower = stronger inhibitory/binding activity).
     */
    private SummaryInsights computeSummary(List<ActivityRecord> records) {
        if (records == null || records.isEmpty()) {
            return SummaryInsights.builder()
                    .totalRecords(0)
                    .uniqueCompounds(0)
                    .mostCommonActivityType("N/A")
                    .top5Compounds(Collections.emptyList())
                    .build();
        }

        // 1. Total records
        int total = records.size();

        // 2. Unique compounds
        long uniqueCompounds = records.stream()
                .map(ActivityRecord::getMoleculeChemblId)
                .filter(Objects::nonNull)
                .distinct()
                .count();

        // 3. Most common activity type
        String mostCommonType = records.stream()
                .map(ActivityRecord::getStandardType)
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(t -> t, Collectors.counting()))
                .entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("N/A");

        // 4. Top 5 compounds by lowest activity value
        // Group by molecule, take best (lowest) value per compound
        List<TopCompound> top5 = records.stream()
                .filter(r -> r.getMoleculeChemblId() != null && r.hasValidValue())
                .collect(Collectors.groupingBy(
                        ActivityRecord::getMoleculeChemblId,
                        Collectors.minBy(Comparator.comparingDouble(
                                r -> r.getStandardValueAsDouble()
                        ))
                ))
                .values().stream()
                .filter(Optional::isPresent)
                .map(Optional::get)
                .sorted(Comparator.comparingDouble(r -> r.getStandardValueAsDouble()))
                .limit(TOP_COMPOUNDS_COUNT)
                .map(r -> new TopCompound(
                        r.getMoleculeChemblId(),
                        r.getStandardType(),
                        r.getStandardValueAsDouble(),
                        r.getStandardUnits(),
                        r.getStandardRelation()
                ))
                .collect(Collectors.toList());

        return SummaryInsights.builder()
                .totalRecords(total)
                .uniqueCompounds((int) uniqueCompounds)
                .mostCommonActivityType(mostCommonType)
                .top5Compounds(top5)
                .build();
    }

    /**
     * Validates that the target ID looks like a valid ChEMBL ID (e.g. CHEMBL203).
     * This is a format check only — the API will return 404 for IDs not in the database.
     */
    private void validateTargetId(String targetId) {
        if (targetId == null || targetId.isBlank()) {
            throw new InvalidTargetIdException("Target ID must not be blank.");
        }
        if (!targetId.toUpperCase().matches("^CHEMBL\\d+$")) {
            throw new InvalidTargetIdException(
                    "Invalid ChEMBL Target ID format: '" + targetId + "'. Expected format: CHEMBL followed by digits (e.g. CHEMBL203)."
            );
        }
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }
}
