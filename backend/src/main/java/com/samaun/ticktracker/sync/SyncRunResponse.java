package com.samaun.ticktracker.sync;

import java.time.Instant;

public record SyncRunResponse(
        Long id,
        TriggerType trigger,
        SyncStatus status,
        Instant startedAt,
        Instant finishedAt,
        int fetched,
        int inserted,
        int duplicates,
        int rejected,
        int corrected,
        String errorMessage
) {
    public static SyncRunResponse from(SyncRun run) {
        return new SyncRunResponse(run.getId(), run.getTriggerType(), run.getStatus(),
                run.getStartedAt(), run.getFinishedAt(), run.getFetched(), run.getInserted(),
                run.getDuplicates(), run.getRejected(), run.getCorrected(), run.getErrorMessage());
    }
}
