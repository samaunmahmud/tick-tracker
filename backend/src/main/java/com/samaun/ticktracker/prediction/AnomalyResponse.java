package com.samaun.ticktracker.prediction;

import java.util.List;

public record AnomalyResponse(
        AnomalyLevel level,
        double alpha,
        int tested,
        int skipped,
        int flagged,
        int flaggedWithoutCorrection,
        double expectedByChanceWithoutCorrection,
        List<Anomaly> anomalies,
        String method,
        String caveat
) {
}
