# Tick Tracker API

A Spring Boot backend for UK tick sightings. It syncs sighting data from an external API into PostgreSQL, cleans and de-duplicates it, accepts new reports, and serves search and aggregate insights.

> **Status:** work in progress.

**Author:** Samaun Mahmud

## Tech stack

| Area | Choice | Why |
|---|---|---|
| Language / framework | Java 21, Spring Boot 3.5 | TODO |
| Database | PostgreSQL 16 + Flyway | TODO |
| API docs | springdoc-openapi (Swagger UI) | TODO |
| Testing | JUnit 5, Mockito, MockMvc, Testcontainers | TODO |
| Running | Docker Compose | TODO |

## Running it

Requires Docker. For local development you also need Java 21.

```bash
# Everything in Docker
docker compose up --build
```

```bash
# Local development
docker compose up -d db
cd backend
./mvnw spring-boot:run
```

- API: http://localhost:8080
- Swagger UI: http://localhost:8080/swagger-ui.html

### Tests

```bash
cd backend && ./mvnw test
```

## Endpoints

TODO

## Design decisions

TODO, see [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Use of AI

TODO

## Video walkthrough

TODO
