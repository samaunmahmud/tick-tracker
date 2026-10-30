package com.samaun.ticktracker.insights;

import java.time.LocalDate;

public record TrendPoint(
        LocalDate periodStart,
        long count
) {
}
