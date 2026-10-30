package com.samaun.ticktracker.prediction;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

public enum AnomalyLevel {
    CITY,
    REGION;

    public static Optional<AnomalyLevel> fromName(String name) {
        if (name == null) {
            return Optional.empty();
        }
        String wanted = name.trim().toUpperCase(Locale.ROOT);
        return Arrays.stream(values()).filter(level -> level.name().equals(wanted)).findFirst();
    }
}
