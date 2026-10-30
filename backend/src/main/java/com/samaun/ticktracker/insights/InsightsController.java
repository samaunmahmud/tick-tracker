package com.samaun.ticktracker.insights;

import com.samaun.ticktracker.sighting.SightingFilterParams;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/insights")
public class InsightsController {

    private final InsightsService insightsService;

    public InsightsController(InsightsService insightsService) {
        this.insightsService = insightsService;
    }

    @GetMapping("/summary")
    public InsightsSummary summary(@ModelAttribute SightingFilterParams params) {
        return insightsService.summary(params);
    }

    @GetMapping("/by-city")
    public List<CountByKey> byCity(@ModelAttribute SightingFilterParams params) {
        return insightsService.countByCity(params);
    }

    @GetMapping("/by-region")
    public List<CountByKey> byRegion(@ModelAttribute SightingFilterParams params) {
        return insightsService.countByRegion(params);
    }

    @GetMapping("/by-species")
    public List<CountByKey> bySpecies(@ModelAttribute SightingFilterParams params) {
        return insightsService.countBySpecies(params);
    }

    @GetMapping("/trends")
    public List<TrendPoint> trends(@ModelAttribute SightingFilterParams params,
                                   @RequestParam(defaultValue = "month") String interval) {
        return insightsService.trends(params, interval);
    }

    @GetMapping("/seasonal")
    public List<SeasonalPoint> seasonal(@ModelAttribute SightingFilterParams params) {
        return insightsService.seasonal(params);
    }
}
