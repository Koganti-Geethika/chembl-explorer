package com.centella.chembl.client;

import com.centella.chembl.dto.ChemblApiResponse;
import com.centella.chembl.exception.ChemblApiException;
import com.centella.chembl.exception.TargetNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * HTTP client layer for communicating with the ChEMBL REST API.
 *
 * Responsibilities:
 * - Build API requests with correct query parameters
 * - Handle HTTP errors: 404 (invalid target), 5xx, timeouts
 * - Paginate transparently to fetch ALL records for a given target+filter combo
 * - Cache results to avoid redundant API calls during the session
 *
 * ChEMBL API max limit per request is 1000. We fetch in pages of 1000
 * and aggregate all records before returning to the service layer.
 */
@Slf4j
@Component
public class ChemblApiClient {

    private static final int PAGE_SIZE = 1000;
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final WebClient webClient;

    public ChemblApiClient(@Value("${chembl.api.base-url}") String baseUrl) {
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .codecs(c -> c.defaultCodecs().maxInMemorySize(10 * 1024 * 1024)) // 10MB buffer
                .build();
    }

    /**
     * Fetches ALL activity records for a given target, handling ChEMBL pagination internally.
     *
     * @param targetId     ChEMBL Target ID (e.g. "CHEMBL203")
     * @param activityType Optional standard_type filter (e.g. "IC50"). Null = no filter.
     * @param assayType    Optional assay_type filter (e.g. "B" for binding). Null = no filter.
     * @param organism     Optional target_organism filter. Null = no filter.
     * @param units        Optional standard_units filter. Null = no filter.
     * @param relation     Optional standard_relation filter (e.g. "="). Null = no filter.
     * @return All matching activity records across all pages
     * @throws TargetNotFoundException if ChEMBL returns 404 for the given target
     * @throws ChemblApiException      on any other API or network failure
     */
    @Cacheable(value = "activities", key = "#targetId + '_' + #activityType + '_' + #assayType + '_' + #organism + '_' + #units + '_' + #relation")
    public List<com.centella.chembl.dto.ActivityRecord> fetchAllActivities(
            String targetId,
            String activityType,
            String assayType,
            String organism,
            String units,
            String relation) {

        log.info("Fetching activities from ChEMBL: target={}, type={}, assayType={}, organism={}",
                targetId, activityType, assayType, organism);

        List<com.centella.chembl.dto.ActivityRecord> allRecords = new ArrayList<>();
        int offset = 0;
        int totalCount = Integer.MAX_VALUE;

        // Paginate through all results
        while (offset < totalCount) {
            ChemblApiResponse page = fetchPage(targetId, activityType, assayType, organism, units, relation, offset, PAGE_SIZE);

            if (page == null || page.getPageMeta() == null) {
                log.warn("Received null or empty page at offset {}", offset);
                break;
            }

            if (page.getActivities() != null) {
                allRecords.addAll(page.getActivities());
            }

            totalCount = page.getPageMeta().getTotalCount() != null
                    ? page.getPageMeta().getTotalCount()
                    : 0;

            log.debug("Fetched page: offset={}, pageSize={}, totalCount={}", offset, PAGE_SIZE, totalCount);

            offset += PAGE_SIZE;

            // Safety guard: if no records returned in this page, stop
            if (page.getActivities() == null || page.getActivities().isEmpty()) {
                break;
            }
        }

        log.info("Total records fetched for target {}: {}", targetId, allRecords.size());
        return allRecords;
    }

    /**
     * Fetches a single page from the ChEMBL API.
     */
    private ChemblApiResponse fetchPage(
            String targetId,
            String activityType,
            String assayType,
            String organism,
            String units,
            String relation,
            int offset,
            int limit) {

        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromPath("/activity.json")
                .queryParam("target_chembl_id", targetId)
                .queryParam("limit", limit)
                .queryParam("offset", offset);

        // Apply optional server-side filters (reduces payload from ChEMBL)
        if (activityType != null && !activityType.isBlank()) {
            uriBuilder.queryParam("standard_type", activityType);
        }
        if (assayType != null && !assayType.isBlank()) {
            uriBuilder.queryParam("assay_type", assayType);
        }
        if (organism != null && !organism.isBlank()) {
            uriBuilder.queryParam("target_organism", organism);
        }
        if (units != null && !units.isBlank()) {
            uriBuilder.queryParam("standard_units", units);
        }
        if (relation != null && !relation.isBlank()) {
            uriBuilder.queryParam("standard_relation", relation);
        }

        String uri = uriBuilder.build().toUriString();
        log.debug("ChEMBL request URI: {}", uri);

        try {
            return webClient.get()
                    .uri(uri)
                    .retrieve()
                    .onStatus(status -> status == HttpStatus.NOT_FOUND, response -> {
                        log.warn("ChEMBL target not found: {}", targetId);
                        return Mono.error(new TargetNotFoundException(
                                "Target ID not found in ChEMBL: " + targetId));
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, response -> {
                        log.error("ChEMBL API server error: {}", response.statusCode());
                        return Mono.error(new ChemblApiException(
                                "ChEMBL API is currently unavailable. Please try again later."));
                    })
                    .bodyToMono(ChemblApiResponse.class)
                    .timeout(TIMEOUT)
                    .block();

        } catch (TargetNotFoundException | ChemblApiException e) {
            throw e;
        } catch (WebClientResponseException e) {
            log.error("WebClient error: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new ChemblApiException("ChEMBL API error: " + e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error calling ChEMBL API", e);
            throw new ChemblApiException("Failed to communicate with ChEMBL API: " + e.getMessage());
        }
    }
}
