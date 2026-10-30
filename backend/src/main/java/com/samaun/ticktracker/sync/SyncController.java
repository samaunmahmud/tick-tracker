package com.samaun.ticktracker.sync;

import com.samaun.ticktracker.common.PageResponse;
import com.samaun.ticktracker.ingestion.IngestionService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/sync")
public class SyncController {

    private final IngestionService ingestionService;
    private final SyncHistoryService syncHistoryService;

    public SyncController(IngestionService ingestionService, SyncHistoryService syncHistoryService) {
        this.ingestionService = ingestionService;
        this.syncHistoryService = syncHistoryService;
    }

    @PostMapping
    public ResponseEntity<SyncStartedResponse> startSync() {
        Long runId = ingestionService.startSyncInBackground(TriggerType.MANUAL);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/runs/{id}")
                .buildAndExpand(runId)
                .toUri();
        return ResponseEntity.accepted().location(location).body(new SyncStartedResponse(runId, SyncStatus.RUNNING));
    }

    @GetMapping("/runs")
    public List<SyncRunResponse> latestRuns() {
        return syncHistoryService.latestRuns();
    }

    @GetMapping("/runs/{id}")
    public SyncRunResponse findRun(@PathVariable Long id) {
        return syncHistoryService.findRun(id);
    }

    @GetMapping("/rejected")
    public PageResponse<RejectedRecordResponse> rejectedRecords(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return syncHistoryService.rejectedRecords(page, size);
    }
}
