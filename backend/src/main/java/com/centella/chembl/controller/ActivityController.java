package com.centella.chembl.controller;

import com.centella.chembl.dto.ActivityResponse;
import com.centella.chembl.service.ActivityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST Controller exposing ChEMBL activity data endpoints.
 *
 * Base path: /api/activities
 *
 * All filter parameters are optional. When omitted, no filtering is applied for that dimension.
 */
@Slf4j
@RestController
@RequestMapping("/api/activities")
@RequiredArgsConstructor
@CrossOrigin(origins = "*") // Allow React dev server; restrict in production
public class ActivityController {

    private final ActivityService activityService;

    /**
     * GET /api/activities
     *
     * Fetch activity records for a given ChEMBL Target ID with optional filtering and pagination.
     *
     * Query Parameters:
     * @param targetId     REQUIRED. ChEMBL Target ID (e.g. CHEMBL203)
     * @param activityType OPTIONAL. Filter by standard_type (IC50, Ki, EC50, etc.)
     * @param assayType    OPTIONAL. Filter by assay_type (B=binding, F=functional, A=ADME, T=toxicity, P=physicochemical)
     * @param organism     OPTIONAL. Filter by target_organism (e.g. "Homo sapiens")
     * @param units        OPTIONAL. Filter by standard_units (e.g. "nM")
     * @param relation     OPTIONAL. Filter by standard_relation (=, <, >, <=, >=, ~, !)
     * @param page         OPTIONAL. Page number, 0-indexed. Default: 0
     * @param pageSize     OPTIONAL. Records per page. Default: 25, max: 100
     *
     * @return 200 with ActivityResponse, or appropriate error response
     */
    @GetMapping
    public ResponseEntity<ActivityResponse> getActivities(
            @RequestParam String targetId,
            @RequestParam(required = false) String activityType,
            @RequestParam(required = false) String assayType,
            @RequestParam(required = false) String organism,
            @RequestParam(required = false) String units,
            @RequestParam(required = false) String relation,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int pageSize) {

        log.info("GET /api/activities — targetId={}, activityType={}, page={}, pageSize={}",
                targetId, activityType, page, pageSize);

        ActivityResponse response = activityService.getActivities(
                targetId, activityType, assayType, organism, units, relation, page, pageSize
        );

        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/activities/health
     *
     * Simple health check endpoint.
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("ChEMBL Explorer API is running");
    }
}
