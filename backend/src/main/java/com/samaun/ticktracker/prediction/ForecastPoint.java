package com.samaun.ticktracker.prediction;

import java.time.YearMonth;

public record ForecastPoint(
        YearMonth month,
        double predicted,
        double low,
        double high
) {
}
