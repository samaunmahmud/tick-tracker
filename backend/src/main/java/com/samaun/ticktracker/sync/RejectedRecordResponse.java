package com.samaun.ticktracker.sync;

import java.time.Instant;

public record RejectedRecordResponse(
        Long id,
        Long syncRunId,
        String externalId,
        String reason,
        String rawJson,
        Instant rejectedAt
) {
    public static RejectedRecordResponse from(RejectedRecord record) {
        return new RejectedRecordResponse(record.getId(), record.getSyncRunId(), record.getExternalId(),
                record.getReason(), record.getRawJson(), record.getRejectedAt());
    }
}
