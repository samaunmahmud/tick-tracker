package com.samaun.ticktracker.common;

public class SyncFailedException extends RuntimeException {
    public SyncFailedException(Long syncRunId, String reason) {
        super("Sync " + syncRunId + " failed: " + reason);
    }
}
