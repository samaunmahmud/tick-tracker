package com.samaun.ticktracker.sync;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "rejected_records")
public class RejectedRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long syncRunId;
    private String externalId;
    private String rawJson;
    private String reason;
    private Instant rejectedAt;

    protected RejectedRecord() {
    }

    public RejectedRecord(Long syncRunId, String externalId, String rawJson, String reason, Instant rejectedAt) {
        this.syncRunId = syncRunId;
        this.externalId = externalId;
        this.rawJson = rawJson;
        this.reason = reason;
        this.rejectedAt = rejectedAt;
    }

    public Long getId() {
        return id;
    }

    public Long getSyncRunId() {
        return syncRunId;
    }

    public String getExternalId() {
        return externalId;
    }

    public String getRawJson() {
        return rawJson;
    }

    public String getReason() {
        return reason;
    }

    public Instant getRejectedAt() {
        return rejectedAt;
    }
}
