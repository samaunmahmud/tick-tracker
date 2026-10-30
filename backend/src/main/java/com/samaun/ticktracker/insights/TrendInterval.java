package com.samaun.ticktracker.insights;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

public enum TrendInterval {
    WEEK("week", "1 week"),
    MONTH("month", "1 month"),
    YEAR("year", "1 year");

    private final String truncateTo;
    private final String step;

    TrendInterval(String truncateTo, String step) {
        this.truncateTo = truncateTo;
        this.step = step;
    }

    public static Optional<TrendInterval> fromName(String name) {
        if (name == null) {
            return Optional.empty();
        }
        String wanted = name.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(interval -> interval.truncateTo.equals(wanted))
                .findFirst();
    }

    public String truncateTo() {
        return truncateTo;
    }

    public String step() {
        return step;
    }
}
