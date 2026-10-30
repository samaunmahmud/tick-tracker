package com.samaun.ticktracker.prediction;

public record ModelScore(
        String model,
        double meanAbsoluteError
) {
}
