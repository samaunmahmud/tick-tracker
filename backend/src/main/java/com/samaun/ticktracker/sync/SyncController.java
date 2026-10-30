package com.samaun.ticktracker.sync;

import com.samaun.ticktracker.common.ApiError;
import com.samaun.ticktracker.common.PageResponse;
import com.samaun.ticktracker.ingestion.IngestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
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

@Tag(name = "Sync", description = "Pull sightings from the Elanco API into the database and inspect past runs")
@RestController
@RequestMapping("/api/sync")
public class SyncController {

    private final IngestionService ingestionService;
    private final SyncHistoryService syncHistoryService;

    public SyncController(IngestionService ingestionService, SyncHistoryService syncHistoryService) {
        this.ingestionService = ingestionService;
        this.syncHistoryService = syncHistoryService;
    }

    @Operation(summary = "Start a sync now",
            description = "Runs in the background and returns straight away. Poll the run in the Location header "
                    + "until its status is SUCCESS or FAILED.")
    @ApiResponse(responseCode = "202", description = "Sync started")
    @ApiResponse(responseCode = "409", description = "A sync is already running",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @PostMapping
    public ResponseEntity<SyncStartedResponse> startSync() {
        Long runId = ingestionService.startSyncInBackground(TriggerType.MANUAL);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/runs/{id}")
                .buildAndExpand(runId)
                .toUri();
        return ResponseEntity.accepted().location(location).body(new SyncStartedResponse(runId, SyncStatus.RUNNING));
    }

    @Operation(summary = "Latest sync runs",
            description = "The 20 most recent runs, newest first, with fetched, inserted, duplicate, rejected "
                    + "and corrected counts.")
    @GetMapping("/runs")
    public List<SyncRunResponse> latestRuns() {
        return syncHistoryService.latestRuns();
    }

    @Operation(summary = "Get one sync run")
    @ApiResponse(responseCode = "200", description = "The run")
    @ApiResponse(responseCode = "404", description = "No run with this id",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @GetMapping("/runs/{id}")
    public SyncRunResponse findRun(@PathVariable Long id) {
        return syncHistoryService.findRun(id);
    }

    @Operation(summary = "Rejected records",
            description = "Records that failed validation during a sync, with the raw JSON and the reason, "
                    + "newest first.")
    @ApiResponse(responseCode = "200", description = "A page of rejected records")
    @ApiResponse(responseCode = "400", description = "page below 0 or size outside 1 to 100",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @GetMapping("/rejected")
    public PageResponse<RejectedRecordResponse> rejectedRecords(
            @Parameter(description = "Page number, starting at 0")
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Page size, 1 to 100")
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return syncHistoryService.rejectedRecords(page, size);
    }
}
