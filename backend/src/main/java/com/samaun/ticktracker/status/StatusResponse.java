package com.samaun.ticktracker.status;

import com.samaun.ticktracker.sync.SyncRunResponse;

import java.time.Instant;

public record StatusResponse(
        String status,
        Instant time,
        long totalSightings,
        SyncRunResponse lastSyncRun
) {
}
