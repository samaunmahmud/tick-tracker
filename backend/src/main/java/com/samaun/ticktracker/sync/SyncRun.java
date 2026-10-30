package com.samaun.ticktracker.sync;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "sync_runs")
public class SyncRun {

    private static final int MAX_ERROR_MESSAGE_LENGTH = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private TriggerType triggerType;

    @Enumerated(EnumType.STRING)
    private SyncStatus status;

    private Instant startedAt;
    private Instant finishedAt;
    private int fetched;
    private int inserted;
    private int duplicates;
    private int rejected;
    private int corrected;
    private String errorMessage;

    protected SyncRun() {
    }

    public static SyncRun start(TriggerType triggerType, Instant startedAt) {
        SyncRun run = new SyncRun();
        run.triggerType = triggerType;
        run.status = SyncStatus.RUNNING;
        run.startedAt = startedAt;
        return run;
    }

    public void recordProcessed(int fetched, int duplicates, int rejected, int corrected) {
        this.fetched = fetched;
        this.duplicates = duplicates;
        this.rejected = rejected;
        this.corrected = corrected;
    }

    public void addInserted(int count) {
        inserted += count;
    }

    public void succeed(Instant finishedAt) {
        this.status = SyncStatus.SUCCESS;
        this.finishedAt = finishedAt;
    }

    public void fail(String errorMessage, Instant finishedAt) {
        this.status = SyncStatus.FAILED;
        this.finishedAt = finishedAt;
        this.errorMessage = errorMessage != null && errorMessage.length() > MAX_ERROR_MESSAGE_LENGTH
                ? errorMessage.substring(0, MAX_ERROR_MESSAGE_LENGTH)
                : errorMessage;
    }

    public Long getId() {
        return id;
    }

    public TriggerType getTriggerType() {
        return triggerType;
    }

    public SyncStatus getStatus() {
        return status;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public int getFetched() {
        return fetched;
    }

    public int getInserted() {
        return inserted;
    }

    public int getDuplicates() {
        return duplicates;
    }

    public int getRejected() {
        return rejected;
    }

    public int getCorrected() {
        return corrected;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
