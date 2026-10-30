package com.samaun.ticktracker.prediction;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/predictions")
public class PredictionController {

    private final PredictionService predictionService;

    public PredictionController(PredictionService predictionService) {
        this.predictionService = predictionService;
    }

    @GetMapping("/forecast")
    public ForecastResponse forecast(
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String species,
            @RequestParam(defaultValue = "6") @Min(1) @Max(24) int months) {
        return predictionService.forecast(city, species, months);
    }

    @GetMapping("/forecast/evaluation")
    public EvaluationResponse evaluation(
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String species) {
        return predictionService.evaluate(city, species);
    }

    @GetMapping("/anomalies")
    public AnomalyResponse anomalies(
            @RequestParam(defaultValue = "0.05") @DecimalMin(value = "0", inclusive = false)
            @DecimalMax(value = "1", inclusive = false) double alpha,
            @RequestParam(defaultValue = "city") String level) {
        return predictionService.anomalies(alpha, level);
    }
}
