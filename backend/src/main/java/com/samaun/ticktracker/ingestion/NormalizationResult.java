package com.samaun.ticktracker.ingestion;

import java.util.List;

public sealed interface NormalizationResult {

    record Accepted(NormalizedSighting sighting, List<String> corrections) implements NormalizationResult {

        public boolean wasCorrected() {
            return !corrections.isEmpty();
        }
    }

    record Rejected(String reason) implements NormalizationResult {
    }
}
