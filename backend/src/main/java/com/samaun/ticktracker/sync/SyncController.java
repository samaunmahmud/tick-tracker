package com.samaun.ticktracker.sync;

import com.samaun.ticktracker.common.PageResponse;
import com.samaun.ticktracker.common.SyncFailedException;
import com.samaun.ticktracker.ingestion.IngestionService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
    public SyncRunResponse runSync() {
        SyncRun run = ingestionService.runSync(TriggerType.MANUAL);
        if (run.getStatus() == SyncStatus.FAILED) {
            throw new SyncFailedException(run.getId(), run.getErrorMessage());
        }
        return SyncRunResponse.from(run);
    }

    @GetMapping("/runs")
    public List<SyncRunResponse> latestRuns() {
        return syncHistoryService.latestRuns();
    }

    @GetMapping("/rejected")
    public PageResponse<RejectedRecordResponse> rejectedRecords(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return syncHistoryService.rejectedRecords(page, size);
    }
}
