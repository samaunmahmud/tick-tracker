# Tick Tracker API

A Spring Boot backend for UK tick sightings. It pulls sightings from the Elanco tick sightings API into its own PostgreSQL database, cleaning, de-duplicating and logging each sync, and then serves search, aggregate insights and simple statistical predictions from that data. It also accepts new sighting reports.

Author: Samaun Mahmud

## Quick start

You need:

- Docker, running
- ports 5432 (Postgres) and 8080 (API) free
- Java 21, only if you want to run the API outside Docker or run the tests

```bash
docker compose up --build
```

This starts Postgres and the API. On startup Flyway creates the schema and the API runs a first sync, which loads the 1000 records from the Elanco API (it takes about a second once the API is up). After that a sync runs every 6 hours, and you can start one by hand with `POST /api/sync`.

- Swagger UI: http://localhost:8080/swagger-ui.html
- Status (record count and last sync): http://localhost:8080/api/status
- Health: http://localhost:8080/actuator/health

[docs/requests.http](docs/requests.http) has a ready-made request for every endpoint, including the 400, 404 and 409 cases. It runs in IntelliJ's HTTP client or VS Code's REST Client.

For local development, run only the database in Docker:

```bash
docker compose up -d db
cd backend
./mvnw spring-boot:run
```

## Running tests

```bash
cd backend
./mvnw test
```

Docker must be running. The database tests use Testcontainers to start a real Postgres, and without Docker those 32 tests are skipped rather than failed, so a green run without Docker is not the full suite. GitHub Actions runs the whole suite on every pull request and every push to `main` (`.github/workflows/ci.yml`).

## What it does

**Ingestion.** A client fetches all sightings from the Elanco API, with timeouts and retries. Each record is checked on its own: it is either cleaned (for example, a city name in the wrong case) or rejected with a reason, and rejected records are stored with their raw JSON. New records are de-duplicated against the batch and the database, then inserted. Every run is recorded in `sync_runs` with its counts, or its error if it failed.

**Search.** `GET /api/sightings` filters by date range, city, region and species, with paging and sorting. Dates can be given as a date or a date-time, and names are matched without caring about case. Bad filters return a 400 with one error per field.

**Reports.** `POST /api/sightings` accepts a new sighting. The server fills in the region, coordinates and latin name, and returns a 409 if the same sighting already exists.

**Insights.** Counts per city, region and species, a summary, trends by week, month or year, and a seasonal profile across the 12 months. All of them take the same filters as search, and the trend series has no gaps (periods with no sightings show as 0).

**Predictions.** A monthly forecast with a range, a back-test that compares the forecast with two simple baselines on the last 12 months, and detection of unusual months per city or region. Every response explains its method and lists caveats, because on this data the honest answer is that there is not much signal (see below).

## Endpoints

All filters are optional. "Filters" means `from`, `to`, `city`, `region` and `species`.

| Method | Path | Purpose | Key params |
|---|---|---|---|
| GET | `/api/status` | API status, total sightings, latest sync run | |
| GET | `/actuator/health` | Health check | |
| GET | `/api/reference/cities` | Known cities with region and coordinates | |
| GET | `/api/reference/species` | Known species with latin name and a short guide | |
| POST | `/api/sync` | Start a sync in the background: 202 with the run id, 409 if one is already running | |
| GET | `/api/sync/runs` | Latest 20 sync runs | |
| GET | `/api/sync/runs/{id}` | One sync run (poll this after `POST /api/sync`) | `id` |
| GET | `/api/sync/rejected` | Records rejected during syncs, with reason and raw JSON | `page`, `size` (max 100) |
| GET | `/api/sightings` | Search sightings | filters, `sort` (`newest`/`oldest`), `page`, `size` (default 20, max 100) |
| GET | `/api/sightings/{id}` | One sighting | `id` (UUID) |
| POST | `/api/sightings` | Report a sighting (multipart form): 201, or 409 if it is a duplicate | `sightedAt`, `city`, `species`, `notes` (optional, up to 500 chars) |
| GET | `/api/insights/summary` | Total, first and last sighting, count per source, last successful sync | filters |
| GET | `/api/insights/by-city` | Count per city, highest first | filters |
| GET | `/api/insights/by-region` | Count per region, highest first | filters |
| GET | `/api/insights/by-species` | Count per species, highest first | filters |
| GET | `/api/insights/trends` | Counts per period, gaps filled with 0 | filters, `interval` (`week`/`month`/`year`, default `month`) |
| GET | `/api/insights/seasonal` | Count for each calendar month, all 12 always present | filters |
| GET | `/api/predictions/forecast` | Monthly forecast with a low/high range | `city`, `species`, `months` (1 to 24, default 6) |
| GET | `/api/predictions/forecast/evaluation` | Back-test of the forecast against two baselines | `city`, `species` |
| GET | `/api/predictions/anomalies` | Unusually high or low months | `level` (`city`/`region`, default `city`), `alpha` (default 0.05) |

Every error comes back in the same JSON shape: `status`, `error`, `message`, `fieldErrors` and `timestamp`.

## Key design decisions

More detail and trade-offs are in [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

- **Sync into my own database instead of calling the Elanco API on every request.** The external API is rate-limited, can't filter by date or aggregate, and returns everything in one array. With a local copy, the API keeps serving the last good data when Elanco is down, filtering and grouping happen in indexed SQL, and cleaning happens once. The cost is that data can be up to 6 hours old, unless someone triggers a sync.
- **Retry with backoff, because I actually hit 429s.** On my first probe, 8 of 25 rapid requests came back 429. The client retries on 429, 5xx and network errors, up to 5 attempts, waiting 5s, 10s, 20s and 40s (capped at 60s), which is about 75s in total. That outlasts a one-minute rate-limit window. If the server sends `Retry-After`, I use that instead. Other 4xx errors are not retried. If every attempt fails, the run is marked FAILED with the reason and the existing data is untouched.
- **Correct what is safe to correct, reject the rest, and keep the rejects.** Some problems are safe to fix, and fixing them counts as a correction: wrong case or extra spaces in a name, a date with a timezone offset (converted to UK time), a date with no time (start of day), or a missing latin or common name when the other one is known. A missing or future date, an unknown city or species, or a common and latin name that disagree get the record rejected. Rejected records go into `rejected_records` with the raw JSON and the reason, so nothing disappears silently. Each record is parsed separately, so one bad record can't fail the whole sync.
- **De-duplicate by external id and by content hash.** The external id catches the same record coming back on the next sync. The content hash (SHA-256 of date, city and species) catches the same sighting under a different id, or a user report that repeats a synced record. Both columns have unique constraints, so the database enforces it too.
- **A city reference table, because there are no coordinates.** The data only gives a city name. I keep the 14 known cities in an enum with their region and coordinates, which gives a region filter and lets anything unknown be rejected. The limit is that a new city in the feed is rejected until I add it.
- **Aggregations in SQL.** Counts, trends and the seasonal profile are SQL queries (`GROUP BY`, `date_trunc`), and `generate_series` fills empty periods with 0 so a chart doesn't skip time. The trend interval comes from an enum, never from raw user input, and a trend is capped at 2000 periods.
- **Background sync with 202, polling and a lock.** `POST /api/sync` returns 202 with a run id straight away, because a sync can take over a minute when it's retrying. You then poll `GET /api/sync/runs/{id}`. An `AtomicBoolean` makes sure only one sync runs at a time, and a second request gets a 409.
- **Honest evaluation of the predictions.** I back-test the forecast on the last 12 months and compare it with two baselines anyone would try first. My model came second, and the API says so in its response instead of hiding it. The anomaly detection corrects for running thousands of tests at once (Benjamini-Hochberg). Without that correction, it would report dozens of "anomalies" that are just noise.

## What I found in the data

- **1000 records, all clean.** They run from 2012-01-03 to 2024-12-30, with no duplicate ids, no duplicate content and no null fields. The first sync inserted 1000, rejected 0 and corrected 0, and later syncs report 1000 duplicates and 0 inserted. I still built the cleaning and rejection path, because a real feed won't stay this clean.
- **14 cities, 10 regions and 5 species.** Each species always appears with the same latin name. There are no coordinates and no timezone on the dates, so I treat them as UK local time.
- **The API has no pagination.** `?limit` and `?page` are ignored, and everything comes back in one array of about 140 KB. Its city and species lookups are case-sensitive and exact (`/city/london` returns `[]`), which is another reason to do filtering on my side.
- **Rate limiting is real but intermittent.** I saw 8 out of 25 rapid requests get a 429 on one check, and 0 out of 100 on another.
- **Region counts:** East Midlands 156, Scotland 151, North West 145, Yorkshire and the Humber 128, West Midlands 82, South West 77, London 76, Wales 71, North East 57, South East 57.
- **Seasonality is flat.** The quietest calendar month is May with 66, and the busiest is December with 100. The calendar month explains only 6 to 9% of the month-to-month variation, and the yearly totals have no clear trend (R² about 0.01).
- **Forecast back-test** (trained on 2012 to 2023, tested on 2024), as mean absolute error in sightings per month:

  | Model | MAE |
  |---|---|
  | historical monthly average | **2.42** |
  | seasonal-trend (mine) | 2.47 |
  | same month last year | 3.67 |

  My seasonal-trend model is no better than the plain average for each calendar month, because there is almost no seasonal pattern or trend for it to use.
- **No anomalies after correction.** At the city level, 2165 city-months were tested. 58 had a raw p below 0.05, which is fewer than the 108 you'd expect by chance alone, and none survived Benjamini-Hochberg. At the region level, 1542 were tested, 40 had a raw p below 0.05 and none survived. The data looks like random variation.

## Tech stack

| Area | Choice | Why |
|---|---|---|
| Language and framework | Java 21, Spring Boot 3.5 | The language I know best; records keep DTOs short, and Spring gives validation, scheduling and the HTTP client |
| Database | PostgreSQL 16 | Real SQL for grouping, `date_trunc` and `generate_series`, plus unique constraints for de-duplication |
| Migrations | Flyway | The schema is versioned SQL, and Hibernate only validates against it |
| Data access | Spring Data JPA, and `NamedParameterJdbcTemplate` for insights | JPA for simple reads and writes, plain SQL where aggregation is clearer in SQL |
| HTTP client | Spring `RestClient` | Built in, with timeouts and easy access to status codes for the retry logic |
| API docs | springdoc-openapi (Swagger UI) | Generated from the code, so it can't drift out of date |
| Testing | JUnit 5, AssertJ, Mockito, MockMvc, Testcontainers | Fast unit tests, controller tests without a server, and SQL tested against a real Postgres |
| Running | Docker Compose | One command starts the database and the API |
| CI | GitHub Actions | Runs the full suite, including the Testcontainers tests, on every PR |

## Testing

There are 177 tests, and all of them pass with Docker running (0 failures, 0 skipped):

- **95 unit tests.** These cover the normaliser (every accept, correct and reject rule), the Elanco client's retry logic using `MockRestServiceServer` with a fake sleeper so nothing actually waits, the ingestion service with mocks, the reference lookups, and the statistics, forecast, back-test and anomaly code.
- **50 MockMvc tests.** These cover each controller's validation, status codes and error format, including that a 500 never leaks internal details.
- **32 Testcontainers tests.** These run against a real Postgres 16: the schema matching the entities, search filters, every insights query including gap filling and date ranges, and the prediction endpoints end to end.

CI runs the same `./mvnw test` on every pull request and every push to `main`.

## What I'd improve with more time

- **Authentication on the sync endpoints.** At the moment anyone can trigger a sync or read rejected records.
- **Real coordinates and PostGIS.** I'd store a location per sighting rather than per city, so the API could answer "sightings within 10 km of here".
- **Caching** of the insight responses. They only change after a sync, so they could be cached and cleared when a sync finishes.
- **Incremental sync.** I'd fetch only new records if the Elanco API ever supports it, instead of downloading everything every 6 hours.
- **Image upload with object storage.** I'd store photos in S3 or similar, checking the file's real type and keeping only a key in the database. I cut this to focus on the core; the `image_filename` column is still in the schema for it.
- **Monitoring and alerting.** I'd add metrics on sync duration, rejects and failures, and an alert when syncs keep failing.
- **Better forecasting on real seasonal data.** Tick activity in the real world is strongly seasonal, but this dataset isn't. On data with a real pattern, I'd try a proper seasonal model and keep the same back-test to prove it beats the baselines.

## Use of AI

I built this with Claude Code as a coding assistant. I made the design decisions, directed each step, and reviewed and tested the code, and Claude Code wrote most of it under that direction. I've gone through the code so that I can explain how and why every part works.

## Video walkthrough

Link: (to be added)
