package com.samaun.ticktracker.prediction;

import java.time.YearMonth;

public record Anomaly(
        String area,
        YearMonth month,
        long observed,
        double expected,
        double pValue,
        double adjustedPValue,
        AnomalyType direction
) {
}
