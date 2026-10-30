package com.samaun.ticktracker.sighting;

import io.swagger.v3.oas.annotations.Parameter;

public record SightingFilterParams(
        @Parameter(description = "Earliest sighting, inclusive. Date (2024-01-31) or date-time (2024-01-31T10:00:00), "
                + "UK local time.")
        String from,
        @Parameter(description = "Latest sighting, inclusive. A date on its own covers the whole day.")
        String to,
        @Parameter(description = "City name, case-insensitive, e.g. London. See /api/reference/cities.")
        String city,
        @Parameter(description = "Region name, case-insensitive, e.g. Scotland or North West.")
        String region,
        @Parameter(description = "Common or latin species name, e.g. Marsh tick or Ixodes apronophorus. "
                + "See /api/reference/species.")
        String species
) {
}
