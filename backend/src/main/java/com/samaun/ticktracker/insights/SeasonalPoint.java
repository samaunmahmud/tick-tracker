package com.samaun.ticktracker.insights;

public record SeasonalPoint(
        int month,
        String monthName,
        long count
) {
}
