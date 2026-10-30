package com.samaun.ticktracker.sighting;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

public record SightingResponse(
        UUID id,
        LocalDateTime sightedAt,
        String city,
        String region,
        double latitude,
        double longitude,
        String species,
        String latinName,
        String notes,
        SightingSource source,
        String externalId,
        Instant createdAt
) {
    public static SightingResponse from(Sighting sighting) {
        return new SightingResponse(sighting.getId(), sighting.getSightedAt(), sighting.getCity(),
                sighting.getRegion(), sighting.getLatitude(), sighting.getLongitude(), sighting.getSpecies(),
                sighting.getLatinName(), sighting.getNotes(), sighting.getSource(), sighting.getExternalId(),
                sighting.getCreatedAt());
    }
}
