package com.samaun.ticktracker.sync;

public record SyncStartedResponse(
        Long runId,
        SyncStatus status
) {
}
