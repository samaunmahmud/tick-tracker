package com.samaun.ticktracker.insights;

import com.samaun.ticktracker.sighting.SightingSource;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;

public record InsightsSummary(
        long totalSightings,
        LocalDateTime firstSighting,
        LocalDateTime lastSighting,
        Map<SightingSource, Long> bySource,
        Instant lastSuccessfulSync
) {
}
