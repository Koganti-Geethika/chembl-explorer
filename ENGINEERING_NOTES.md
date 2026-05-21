# Engineering Notes — ChEMBL Target Activity Explorer

## Implementation Approach

The application is structured as a clean separation between backend (Java/Spring Boot) and frontend (React/Vite). The backend acts as an intelligent proxy and aggregator on top of the public ChEMBL REST API, while the frontend provides a usable, responsive interface for exploring the data.

---

## Architecture Decisions

### 1. WebClient over RestTemplate
Spring's `WebClient` was chosen over the older `RestTemplate` because:
- It's the current Spring recommendation for HTTP clients
- Non-blocking by design (even though we block at the service boundary, it's future-proof for reactive migration)
- Better timeout and error handling ergonomics

### 2. Hybrid Filtering Strategy
Filtering is applied at **both levels**:
- **Server-side (ChEMBL API)**: `activityType`, `assayType`, `organism`, `units`, `relation` are all sent as ChEMBL query parameters. This reduces payload size significantly — instead of fetching 5000 records and filtering 4900 of them away, ChEMBL returns only matching records.
- **Client-side (in-memory)**: Summary computation (top compounds, type frequency) is performed in the service layer after fetching. This gives us full control over analytics without multiple round trips.

### 3. In-Memory Pagination
We fetch all matching records from ChEMBL (via internal pagination in 1000-record chunks), then paginate in-memory before returning to the frontend. This was chosen because:
- Summary insights (top 5 compounds, unique compound count) require seeing **all** records, not just the current page
- ChEMBL's own pagination is an implementation detail the consumer shouldn't deal with
- Caffeine cache ensures subsequent page requests for the same query are instant

The tradeoff is higher memory use for very large result sets. This is acceptable for a drug discovery explorer (typical target has ~100–5000 records), but would need streaming for truly massive datasets.

### 4. Caffeine Caching
Results are cached for 10 minutes keyed by all filter parameters. ChEMBL is a scientific database where data changes infrequently — 10 minutes is a good balance between freshness and API load. Max 100 cache entries prevents memory bloat.

### 5. Layered Architecture
Controller → Service → ApiClient is maintained strictly:
- Controller handles HTTP: routing, parameter binding, response codes
- Service handles business logic: validation, filtering, aggregation, pagination
- ApiClient handles I/O: ChEMBL communication, retries, error translation

This makes each layer independently testable and replaceable.

---

## Challenges Faced

### ChEMBL Data Quality
ChEMBL data is real scientific data — it's messy. Many records have:
- `standard_value` = null (assay ran but no numeric result)
- `standard_value` = non-numeric strings like ">" prefixes in some old records
- Duplicate compound records across different assays
- Mixed units (nM vs µM vs µg/mL) for the same target

Handled via: `getStandardValueAsDouble()` with null-safe parsing, excluding invalid records from ranking, and documenting the deduplication strategy in assumptions.

### Pagination Transparency
ChEMBL returns a maximum of 1000 records per request. For targets with thousands of activities (like CHEMBL203 — Epidermal growth factor receptor — which has thousands of records), we must paginate ChEMBL internally. This is hidden from the API consumer.

### CORS in Development
ChEMBL's public API does support CORS for browser requests. For the Spring Boot backend, `@CrossOrigin(origins = "*")` was used for development simplicity. In production this should be restricted.

---

## Tradeoffs Made

| Decision | Chosen | Alternative | Why |
|----------|--------|-------------|-----|
| HTTP client | WebClient | RestTemplate | Modern, future-ready |
| Filtering | Hybrid | Backend-only or Frontend-only | Balance of performance and analytics accuracy |
| Pagination | In-memory after full fetch | Per-page ChEMBL requests | Summary requires full dataset |
| Cache | Caffeine (in-process) | Redis | No infra dependency, sufficient for single-instance |
| ID validation | Regex format check | No validation | Prevents unnecessary API calls for clearly invalid IDs |

---

## Bugs / Issues Encountered

- **ChEMBL 404 behavior**: When a valid-format but non-existent target ID is queried, ChEMBL returns an empty `activities: []` with `total_count: 0`, not a 404. The 404 handling in the client is for genuine API routing errors. Empty result sets are surfaced to the user as "no records found."
- **Numeric value edge cases**: Some `standard_value` entries in ChEMBL contain relational prefixes ("> 10000") — these are stripped and only the clean numeric form is used, with the `standard_relation` field preserved separately.

---

## Improvements with More Time

1. **Streaming / SSE**: For very large targets, stream records to the frontend progressively rather than waiting for full fetch
2. **Redis Cache**: Replace Caffeine with Redis for multi-instance deployability
3. **Compound Structure Images**: ChEMBL provides molecule structure images — these could be fetched and displayed in the activity table
4. **Advanced Analytics**: Distribution charts (histogram of IC50 values), scatter plots (pChEMBL value vs compound), activity cliff detection
5. **Export**: CSV/Excel export of filtered results
6. **Target Search**: Add a target search endpoint so users can search by target name rather than needing to know the ChEMBL ID
7. **Rate Limiting**: Add rate limiting on the backend to protect against abuse of the ChEMBL API
8. **Integration Tests**: Add Wiremock-based integration tests that mock the ChEMBL API responses
9. **OpenAPI/Swagger docs**: Auto-generated API documentation via springdoc-openapi
