package com.samaun.ticktracker.ingestion;

import com.samaun.ticktracker.reference.TickSpecies;
import com.samaun.ticktracker.reference.UkCity;

import java.time.LocalDateTime;

public record NormalizedSighting(
        String externalId,
        LocalDateTime sightedAt,
        UkCity city,
        TickSpecies species,
        String contentHash
) {
}
