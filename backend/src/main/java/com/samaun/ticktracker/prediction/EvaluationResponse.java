package com.samaun.ticktracker.prediction;

import java.time.YearMonth;
import java.util.List;

public record EvaluationResponse(
        String city,
        String species,
        YearMonth trainedFrom,
        YearMonth trainedTo,
        YearMonth testedFrom,
        YearMonth testedTo,
        List<ModelScore> scores,
        String bestModel,
        List<EvaluationMonth> months,
        String method,
        String caveat
) {
}
