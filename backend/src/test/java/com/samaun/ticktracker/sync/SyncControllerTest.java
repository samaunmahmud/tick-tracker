package com.samaun.ticktracker.sync;

import com.samaun.ticktracker.common.NotFoundException;
import com.samaun.ticktracker.common.PageResponse;
import com.samaun.ticktracker.common.SyncInProgressException;
import com.samaun.ticktracker.config.ClockConfig;
import com.samaun.ticktracker.ingestion.IngestionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SyncController.class)
@Import(ClockConfig.class)
class SyncControllerTest {

    private static final Instant STARTED = Instant.parse("2026-10-30T12:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IngestionService ingestionService;

    @MockitoBean
    private SyncHistoryService syncHistoryService;

    @Test
    void manualSyncStartsInBackgroundAndReturnsAccepted() throws Exception {
        when(ingestionService.startSyncInBackground(TriggerType.MANUAL)).thenReturn(7L);

        mockMvc.perform(post("/api/sync"))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location", endsWith("/api/sync/runs/7")))
                .andExpect(jsonPath("$.runId").value(7))
                .andExpect(jsonPath("$.status").value("RUNNING"));
    }

    @Test
    void findsOneRunById() throws Exception {
        SyncRun run = SyncRun.start(TriggerType.MANUAL, STARTED);
        run.fail("Elanco API still failing after 5 attempts", STARTED.plusSeconds(75));
        when(syncHistoryService.findRun(7L)).thenReturn(SyncRunResponse.from(run));

        mockMvc.perform(get("/api/sync/runs/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.errorMessage").value("Elanco API still failing after 5 attempts"));
    }

    @Test
    void unknownRunReturns404() throws Exception {
        when(syncHistoryService.findRun(99L)).thenThrow(new NotFoundException("No sync run with id 99"));

        mockMvc.perform(get("/api/sync/runs/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void manualSyncWhileAnotherIsRunningReturnsConflict() throws Exception {
        when(ingestionService.startSyncInBackground(TriggerType.MANUAL)).thenThrow(new SyncInProgressException());

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

    @Test
    void nonNumericRunIdIsReportedAsAFieldError() throws Exception {
        mockMvc.perform(get("/api/sync/runs/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("id"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("has an invalid value 'abc'"))
                .andExpect(content().string(not(containsString("java."))));
    }

    @Test
    void nonNumericPageSizeIsReportedAsAFieldError() throws Exception {
        mockMvc.perform(get("/api/sync/rejected").param("size", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("size"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("has an invalid value 'abc'"));
    }

    @Test
    void unexpectedErrorReturns500WithoutInternalDetails() throws Exception {
        when(syncHistoryService.latestRuns())
                .thenThrow(new IllegalStateException("connection to db failed, password=hunter2"));

        mockMvc.perform(get("/api/sync/runs"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.message").value("Something went wrong"))
                .andExpect(jsonPath("$.fieldErrors").isEmpty())
                .andExpect(content().string(not(containsString("hunter2"))))
                .andExpect(content().string(not(containsString("IllegalStateException"))));
    }
}
