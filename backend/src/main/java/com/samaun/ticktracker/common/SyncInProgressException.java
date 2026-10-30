package com.samaun.ticktracker.common;

public class SyncInProgressException extends RuntimeException {
    public SyncInProgressException() {
        super("A sync is already running, try again when it finishes");
    }
}
