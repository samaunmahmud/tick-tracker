package com.samaun.ticktracker.status;

import com.samaun.ticktracker.sighting.SightingService;
import com.samaun.ticktracker.sync.SyncHistoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;

@Tag(name = "Status", description = "Quick check that the API is up and how fresh its data is")
@RestController
@RequestMapping("/api/status")
public class StatusController {

    private final SightingService sightingService;
    private final SyncHistoryService syncHistoryService;
    private final Clock clock;

    public StatusController(SightingService sightingService, SyncHistoryService syncHistoryService, Clock clock) {
        this.sightingService = sightingService;
        this.syncHistoryService = syncHistoryService;
        this.clock = clock;
    }

    @Operation(summary = "API status",
            description = "Server time, total sightings stored, and the most recent sync run in any state "
                    + "(null if no sync has run yet).")
    @GetMapping
    public StatusResponse status() {
        return new StatusResponse("ok", clock.instant(), sightingService.totalSightings(),
                syncHistoryService.latestRun().orElse(null));
    }
}
