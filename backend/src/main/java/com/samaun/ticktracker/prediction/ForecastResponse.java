package com.samaun.ticktracker.prediction;

import java.time.YearMonth;
import java.util.List;

public record ForecastResponse(
        String city,
        String species,
        YearMonth trainedFrom,
        YearMonth trainedTo,
        double trendPerYear,
        double seasonalStrength,
        List<ForecastPoint> forecast,
        String method,
        String caveat
) {
}
