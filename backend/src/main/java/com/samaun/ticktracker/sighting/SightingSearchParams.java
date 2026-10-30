package com.samaun.ticktracker.sighting;

public record SightingSearchParams(
        String from,
        String to,
        String city,
        String region,
        String species,
        String sort
) {
}
