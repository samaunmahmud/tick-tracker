package com.samaun.ticktracker.insights;

import com.samaun.ticktracker.common.ApiError;
import com.samaun.ticktracker.sighting.SightingFilterParams;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Insights", description = "Counts and trends computed in SQL. Every endpoint accepts the same optional "
        + "filters as sighting search.")
@ApiResponse(responseCode = "200", description = "OK")
@ApiResponse(responseCode = "400", description = "Unknown city, region, species or interval, a bad date, "
        + "or 'from' after 'to'",
        content = @Content(schema = @Schema(implementation = ApiError.class)))
@RestController
@RequestMapping("/api/insights")
public class InsightsController {

    private final InsightsService insightsService;

    public InsightsController(InsightsService insightsService) {
        this.insightsService = insightsService;
    }

    @Operation(summary = "Summary",
            description = "Total sightings, first and last sighting date, count per source, and the last "
                    + "successful sync time.")
    @GetMapping("/summary")
    public InsightsSummary summary(@ParameterObject @ModelAttribute SightingFilterParams params) {
        return insightsService.summary(params);
    }

    @Operation(summary = "Sightings per city", description = "Sorted by count, highest first.")
    @GetMapping("/by-city")
    public List<CountByKey> byCity(@ParameterObject @ModelAttribute SightingFilterParams params) {
        return insightsService.countByCity(params);
    }

    @Operation(summary = "Sightings per region", description = "Sorted by count, highest first.")
    @GetMapping("/by-region")
    public List<CountByKey> byRegion(@ParameterObject @ModelAttribute SightingFilterParams params) {
        return insightsService.countByRegion(params);
    }

    @Operation(summary = "Sightings per species", description = "Sorted by count, highest first.")
    @GetMapping("/by-species")
    public List<CountByKey> bySpecies(@ParameterObject @ModelAttribute SightingFilterParams params) {
        return insightsService.countBySpecies(params);
    }

    @Operation(summary = "Trend over time",
            description = "Sightings per week, month or year. The series covers 'from' to 'to' when given, "
                    + "otherwise the first to the last matching sighting. Periods with no sightings are included "
                    + "with a count of 0 so the series has no gaps. At most 2000 periods are returned.")
    @GetMapping("/trends")
    public List<TrendPoint> trends(@ParameterObject @ModelAttribute SightingFilterParams params,
                                   @Parameter(description = "week, month (default) or year")
                                   @RequestParam(defaultValue = "month") String interval) {
        return insightsService.trends(params, interval);
    }

    @Operation(summary = "Seasonal pattern",
            description = "Sightings per calendar month across all years. Always returns all 12 months.")
    @GetMapping("/seasonal")
    public List<SeasonalPoint> seasonal(@ParameterObject @ModelAttribute SightingFilterParams params) {
        return insightsService.seasonal(params);
    }
}
