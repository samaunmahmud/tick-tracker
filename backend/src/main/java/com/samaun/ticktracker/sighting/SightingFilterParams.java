package com.samaun.ticktracker.sighting;

public record SightingFilterParams(
        String from,
        String to,
        String city,
        String region,
        String species
) {
}
