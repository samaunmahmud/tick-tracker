package com.samaun.ticktracker.prediction;

import java.time.YearMonth;

public record EvaluationMonth(
        YearMonth month,
        double actual,
        double seasonalTrend,
        double sameMonthLastYear,
        double historicalMonthlyAverage
) {
}
