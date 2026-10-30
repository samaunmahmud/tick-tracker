package com.samaun.ticktracker.sync;

import com.samaun.ticktracker.common.PageResponse;
import com.samaun.ticktracker.common.SyncInProgressException;
import com.samaun.ticktracker.ingestion.IngestionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SyncController.class)
class SyncControllerTest {

    private static final Instant STARTED = Instant.parse("2026-10-30T12:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IngestionService ingestionService;

    @MockitoBean
    private SyncHistoryService syncHistoryService;

    @Test
    void manualSyncReturnsRunSummary() throws Exception {
        SyncRun run = SyncRun.start(TriggerType.MANUAL, STARTED);
        run.recordProcessed(10, 2, 1, 3);
        run.addInserted(7);
        run.succeed(STARTED.plusSeconds(2));
        when(ingestionService.runSync(TriggerType.MANUAL)).thenReturn(run);

        mockMvc.perform(post("/api/sync"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.trigger").value("MANUAL"))
                .andExpect(jsonPath("$.fetched").value(10))
                .andExpect(jsonPath("$.inserted").value(7))
                .andExpect(jsonPath("$.duplicates").value(2));
    }

    @Test
    void failedManualSyncReturnsBadGateway() throws Exception {
        SyncRun run = SyncRun.start(TriggerType.MANUAL, STARTED);
        run.fail("Elanco API still failing after 5 attempts", STARTED.plusSeconds(75));
        when(ingestionService.runSync(TriggerType.MANUAL)).thenReturn(run);

        mockMvc.perform(post("/api/sync"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.status").value(502))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("still failing")));
    }

    @Test
    void manualSyncWhileAnotherIsRunningReturnsConflict() throws Exception {
        when(ingestionService.runSync(TriggerType.MANUAL)).thenThrow(new SyncInProgressException());

        mockMvc.perform(post("/api/sync"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void listsLatestRuns() throws Exception {
        when(syncHistoryService.latestRuns()).thenReturn(List.of(
                SyncRunResponse.from(SyncRun.start(TriggerType.STARTUP, STARTED))));

        mockMvc.perform(get("/api/sync/runs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].trigger").value("STARTUP"))
                .andExpect(jsonPath("$[0].status").value("RUNNING"));
    }

    @Test
    void listsRejectedRecordsPage() throws Exception {
        RejectedRecordResponse reject = new RejectedRecordResponse(1L, 4L, "bad1", "Unknown city 'Atlantis'",
                "{\"id\":\"bad1\"}", STARTED);
        when(syncHistoryService.rejectedRecords(0, 20)).thenReturn(new PageResponse<>(List.of(reject), 0, 20, 1, 1));

        mockMvc.perform(get("/api/sync/rejected"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].reason").value("Unknown city 'Atlantis'"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void rejectsPageSizeOverOneHundred() throws Exception {
        mockMvc.perform(get("/api/sync/rejected").param("size", "500"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("size"));
    }
}
