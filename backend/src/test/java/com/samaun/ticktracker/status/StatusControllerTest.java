package com.samaun.ticktracker.status;

import com.samaun.ticktracker.config.ClockConfig;
import com.samaun.ticktracker.sighting.SightingService;
import com.samaun.ticktracker.sync.SyncHistoryService;
import com.samaun.ticktracker.sync.SyncRunResponse;
import com.samaun.ticktracker.sync.SyncStatus;
import com.samaun.ticktracker.sync.TriggerType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StatusController.class)
@Import(StatusControllerTest.FixedClock.class)
class StatusControllerTest {

    private static final Instant NOW = Instant.parse("2026-10-30T12:00:00Z");

    @TestConfiguration
    static class FixedClock {
        @Bean
        Clock clock() {
            return Clock.fixed(NOW, ClockConfig.UK);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SightingService sightingService;

    @MockitoBean
    private SyncHistoryService syncHistoryService;

    @Test
    void statusReportsTotalSightingsAndLatestSyncRun() throws Exception {
        when(sightingService.totalSightings()).thenReturn(1000L);
        when(syncHistoryService.latestRun()).thenReturn(Optional.of(new SyncRunResponse(3L, TriggerType.MANUAL,
                SyncStatus.SUCCESS, NOW.minusSeconds(60), NOW.minusSeconds(55), 1000, 0, 1000, 0, 0, null)));

        mockMvc.perform(get("/api/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.time").value("2026-10-30T12:00:00Z"))
                .andExpect(jsonPath("$.totalSightings").value(1000))
                .andExpect(jsonPath("$.lastSyncRun.id").value(3))
                .andExpect(jsonPath("$.lastSyncRun.status").value("SUCCESS"))
                .andExpect(jsonPath("$.lastSyncRun.duplicates").value(1000));
    }

    @Test
    void statusShowsNullLastSyncRunBeforeAnySync() throws Exception {
        when(sightingService.totalSightings()).thenReturn(0L);
        when(syncHistoryService.latestRun()).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSightings").value(0))
                .andExpect(jsonPath("$.lastSyncRun").isEmpty());
    }

    @Test
    void unknownRouteReturnsJson404() throws Exception {
        mockMvc.perform(get("/api/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }
}
