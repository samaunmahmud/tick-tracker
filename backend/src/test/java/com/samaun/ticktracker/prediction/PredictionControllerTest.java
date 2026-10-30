package com.samaun.ticktracker.prediction;

import com.samaun.ticktracker.common.ApiError.FieldError;
import com.samaun.ticktracker.common.InvalidRequestException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PredictionController.class)
class PredictionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PredictionService predictionService;

    @Test
    void forecastDefaultsToSixMonths() throws Exception {
        when(predictionService.forecast(isNull(), isNull(), anyInt()))
                .thenReturn(new ForecastResponse(null, null, null, null, 0, 0, List.of(), "method", null));

        mockMvc.perform(get("/api/predictions/forecast")).andExpect(status().isOk());

        verify(predictionService).forecast(null, null, 6);
    }

    @Test
    void forecastRejectsZeroMonths() throws Exception {
        mockMvc.perform(get("/api/predictions/forecast").param("months", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("months"));
    }

    @Test
    void forecastRejectsMoreThanTwentyFourMonths() throws Exception {
        mockMvc.perform(get("/api/predictions/forecast").param("months", "25"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("months"));
    }

    @Test
    void forecastRejectsUnknownCity() throws Exception {
        when(predictionService.forecast(any(), any(), anyInt()))
                .thenThrow(new InvalidRequestException(List.of(new FieldError("city", "unknown city 'Atlantis'"))));

        mockMvc.perform(get("/api/predictions/forecast").param("city", "Atlantis"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].message").value("unknown city 'Atlantis'"));
    }

    @Test
    void anomaliesDefaultToCityLevelAtFivePercent() throws Exception {
        when(predictionService.anomalies(0.05, "city")).thenReturn(new AnomalyResponse(AnomalyLevel.CITY, 0.05,
                10, 0, 0, 1, 0.5, List.of(), "method", "caveat"));

        mockMvc.perform(get("/api/predictions/anomalies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.level").value("CITY"))
                .andExpect(jsonPath("$.tested").value(10));
    }

    @Test
    void anomaliesRejectAlphaOutsideZeroToOne() throws Exception {
        for (String alpha : new String[]{"0", "1", "-0.1", "1.5"}) {
            mockMvc.perform(get("/api/predictions/anomalies").param("alpha", alpha))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors[0].field").value("alpha"));
        }
    }

    @Test
    void anomaliesRejectNonNumericAlpha() throws Exception {
        mockMvc.perform(get("/api/predictions/anomalies").param("alpha", "low"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void anomaliesRejectUnknownLevel() throws Exception {
        when(predictionService.anomalies(0.05, "country"))
                .thenThrow(new InvalidRequestException(List.of(new FieldError("level", "must be 'city' or 'region'"))));

        mockMvc.perform(get("/api/predictions/anomalies").param("level", "country"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("level"));
    }

    @Test
    void evaluationPassesFiltersThrough() throws Exception {
        when(predictionService.evaluate("Leeds", "Marsh tick")).thenReturn(new EvaluationResponse("Leeds", "Marsh tick",
                null, null, null, null, List.of(new ModelScore("seasonal-trend", 1.5)), "seasonal-trend", List.of(),
                "method", null));

        mockMvc.perform(get("/api/predictions/forecast/evaluation").param("city", "Leeds").param("species", "Marsh tick"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bestModel").value("seasonal-trend"))
                .andExpect(jsonPath("$.scores[0].meanAbsoluteError").value(1.5));
    }
}
