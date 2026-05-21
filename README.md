# ChEMBL Target Activity Explorer

A full-stack application for exploring biological activity data from the [ChEMBL database](https://www.ebi.ac.uk/chembl/). Search any ChEMBL target, filter by activity type, and view compound potency insights.

---

## Architecture Overview

```
┌──────────────────────────────────┐
│         React Frontend           │
│  (Vite + React 18, port 5173)    │
└────────────┬─────────────────────┘
             │ HTTP (REST)
┌────────────▼─────────────────────┐
│      Spring Boot Backend         │
│         (port 8080)              │
│                                  │
│  ┌──────────────────────────┐    │
│  │    ActivityController    │    │
│  └────────────┬─────────────┘    │
│               │                  │
│  ┌────────────▼─────────────┐    │
│  │     ActivityService      │    │
│  │  (filtering + summary)   │    │
│  └────────────┬─────────────┘    │
│               │                  │
│  ┌────────────▼─────────────┐    │
│  │    ChemblApiClient       │    │
│  │  (WebClient + caching)   │    │
│  └────────────┬─────────────┘    │
└───────────────┼──────────────────┘
                │ HTTPS
┌───────────────▼──────────────────┐
│         ChEMBL REST API          │
│  https://www.ebi.ac.uk/chembl/   │
└──────────────────────────────────┘
```

---

## Environment Requirements

| Component | Version |
|-----------|---------|
| Java      | 17+     |
| Maven     | 3.8+    |
| Node.js   | 18+     |
| npm       | 9+      |

---

## Running the Backend

```bash
cd backend

# Build
mvn clean install

# Run
mvn spring-boot:run

# The API will be available at:
# http://localhost:8080/api/activities
```

### Example API Calls

```bash
# Basic fetch
curl "http://localhost:8080/api/activities?targetId=CHEMBL203"

# With activity type filter
curl "http://localhost:8080/api/activities?targetId=CHEMBL203&activityType=IC50"

# With pagination
curl "http://localhost:8080/api/activities?targetId=CHEMBL203&activityType=IC50&page=0&pageSize=10"

# With multiple filters
curl "http://localhost:8080/api/activities?targetId=CHEMBL203&activityType=IC50&organism=Homo+sapiens&units=nM"
```

---

## Running the Frontend

```bash
cd frontend

# Install dependencies
npm install

# Start dev server
npm run dev

# Opens at: http://localhost:5173
```

### Environment Variables (frontend)

Create a `.env` file in the `frontend/` directory:

```
VITE_API_BASE_URL=http://localhost:8080
```

---

## Running Tests

```bash
cd backend
mvn test
```

---

## Docker (Optional)

```bash
# Build and run everything
docker-compose up --build
```

See `docker-compose.yml` for service configuration.

---

## API Reference

### `GET /api/activities`

| Parameter    | Type   | Required | Description                                   |
|--------------|--------|----------|-----------------------------------------------|
| targetId     | string | ✅ Yes   | ChEMBL Target ID (e.g. `CHEMBL203`)           |
| activityType | string | No       | IC50, Ki, EC50, Kd, etc.                      |
| assayType    | string | No       | B (binding), F (functional), A (ADME), etc.   |
| organism     | string | No       | e.g. `Homo sapiens`                           |
| units        | string | No       | e.g. `nM`, `uM`                               |
| relation     | string | No       | `=`, `<`, `>`, `<=`, `>=`                     |
| page         | int    | No       | 0-indexed page. Default: 0                    |
| pageSize     | int    | No       | Records per page. Default: 25, max: 100       |

### Response Structure

```json
{
  "status": "success",
  "targetId": "CHEMBL203",
  "totalRecords": 1523,
  "page": 0,
  "pageSize": 25,
  "totalPages": 61,
  "activities": [ ... ],
  "summary": {
    "totalRecords": 1523,
    "uniqueCompounds": 1201,
    "mostCommonActivityType": "IC50",
    "top5Compounds": [
      {
        "moleculeChemblId": "CHEMBL12345",
        "activityType": "IC50",
        "activityValue": 0.3,
        "units": "nM",
        "relation": "="
      }
    ]
  },
  "appliedFilters": {
    "activityType": "IC50",
    "assayType": null,
    "organism": null,
    "standardUnits": null,
    "relation": null
  }
}
```

---

## Assumptions Made

1. **Top 5 Strongest Compounds**: Ranked by lowest numeric `standard_value`. When a compound appears in multiple records, its best (lowest) value is used. Records with non-numeric or null values are excluded from ranking.

2. **Unique Compounds**: Counted by distinct `molecule_chembl_id`. Records missing this field are excluded.

3. **Most Common Activity Type**: Frequency-based count of `standard_type`. Null types excluded.

4. **Filtering Strategy**: Hybrid — server-side filters are sent directly to ChEMBL API as query parameters (reducing data transfer). All records are fetched before pagination to enable accurate summary computation.

5. **Target ID Validation**: Format-validated client-side (`CHEMBL` + digits). The API handles 404 for valid-format but non-existent IDs.

6. **Pagination**: ChEMBL API is paginated in 1000-record chunks internally. Our API exposes configurable pagination (default 25/page) to the frontend.

7. **CORS**: Currently set to `*` for development. Should be restricted to the frontend domain in production.
