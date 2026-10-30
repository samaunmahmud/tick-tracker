package com.samaun.ticktracker.prediction;

import com.samaun.ticktracker.common.ApiError;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Predictions", description = "Monthly forecast, its back-test, and anomaly detection. Plain statistics "
        + "on the stored data; each response explains its method and lists caveats.")
@ApiResponse(responseCode = "200", description = "OK")
@ApiResponse(responseCode = "400", description = "A parameter is out of range or not recognised",
        content = @Content(schema = @Schema(implementation = ApiError.class)))
@RestController
@RequestMapping("/api/predictions")
public class PredictionController {

    private final PredictionService predictionService;

    public PredictionController(PredictionService predictionService) {
        this.predictionService = predictionService;
    }

    @Operation(summary = "Forecast monthly sightings",
            description = "Seasonal profile times a linear trend on yearly totals, with a range of one standard "
                    + "deviation.")
    @GetMapping("/forecast")
    public ForecastResponse forecast(
            @Parameter(description = "Optional city to forecast for")
            @RequestParam(required = false) String city,
            @Parameter(description = "Optional species to forecast for")
            @RequestParam(required = false) String species,
            @Parameter(description = "How many months ahead, 1 to 24")
            @RequestParam(defaultValue = "6") @Min(1) @Max(24) int months) {
        return predictionService.forecast(city, species, months);
    }

    @Operation(summary = "Back-test the forecast",
            description = "Hides the last 12 months, forecasts them from earlier data, and compares the model with "
                    + "two simple baselines by mean absolute error.")
    @GetMapping("/forecast/evaluation")
    public EvaluationResponse evaluation(
            @Parameter(description = "Optional city to evaluate for")
            @RequestParam(required = false) String city,
            @Parameter(description = "Optional species to evaluate for")
            @RequestParam(required = false) String species) {
        return predictionService.evaluate(city, species);
    }

    @Operation(summary = "Unusual months",
            description = "Poisson test of each area-month against the same month in other years, with "
                    + "Benjamini-Hochberg correction across all tests. Only results still significant after "
                    + "correction are listed.")
    @GetMapping("/anomalies")
    public AnomalyResponse anomalies(
            @Parameter(description = "False discovery rate, strictly between 0 and 1")
            @RequestParam(defaultValue = "0.05") @DecimalMin(value = "0", inclusive = false)
            @DecimalMax(value = "1", inclusive = false) double alpha,
            @Parameter(description = "city (default) or region")
            @RequestParam(defaultValue = "city") String level) {
        return predictionService.anomalies(alpha, level);
    }
}
