package com.samaun.ticktracker.insights;

import com.samaun.ticktracker.common.ApiError.FieldError;
import com.samaun.ticktracker.common.InvalidRequestException;
import com.samaun.ticktracker.sighting.SightingFilterParams;
import com.samaun.ticktracker.sighting.SightingSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InsightsController.class)
class InsightsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InsightsService insightsService;

    @Test
    void summaryReturnsTotalsAndSources() throws Exception {
        when(insightsService.summary(any())).thenReturn(new InsightsSummary(1000,
                LocalDateTime.of(2012, 1, 3, 20, 40, 43), LocalDateTime.of(2024, 12, 30, 4, 32, 12),
                Map.of(SightingSource.ELANCO_API, 1000L, SightingSource.USER_REPORT, 0L), null));

        mockMvc.perform(get("/api/insights/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSightings").value(1000))
                .andExpect(jsonPath("$.bySource.ELANCO_API").value(1000))
                .andExpect(jsonPath("$.firstSighting").value("2012-01-03T20:40:43"));
    }

    @Test
    void byRegionPassesFiltersThrough() throws Exception {
        SightingFilterParams expected = new SightingFilterParams("2024-01-01", null, null, null, "Marsh tick");
        when(insightsService.countByRegion(expected)).thenReturn(List.of(new CountByKey("North West", 12)));

        mockMvc.perform(get("/api/insights/by-region").param("from", "2024-01-01").param("species", "Marsh tick"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].key").value("North West"))
                .andExpect(jsonPath("$[0].count").value(12));
    }

    @Test
    void trendsDefaultToMonthly() throws Exception {
        when(insightsService.trends(any(), eq("month")))
                .thenReturn(List.of(new TrendPoint(LocalDate.of(2024, 1, 1), 3)));

        mockMvc.perform(get("/api/insights/trends"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].periodStart").value("2024-01-01"))
                .andExpect(jsonPath("$[0].count").value(3));
    }

    @Test
    void invalidIntervalReturns400WithFieldError() throws Exception {
        when(insightsService.trends(any(), eq("fortnight")))
                .thenThrow(new InvalidRequestException(List.of(new FieldError("interval", "must be 'week', 'month' or 'year'"))));

        mockMvc.perform(get("/api/insights/trends").param("interval", "fortnight"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("interval"));
    }

    @Test
    void seasonalReturnsMonths() throws Exception {
        when(insightsService.seasonal(any())).thenReturn(List.of(new SeasonalPoint(1, "January", 80)));

        mockMvc.perform(get("/api/insights/seasonal"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].monthName").value("January"));
    }
}
