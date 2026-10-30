CREATE TABLE sightings (
    id              UUID PRIMARY KEY,
    external_id     VARCHAR(64) UNIQUE,
    content_hash    VARCHAR(64) NOT NULL UNIQUE,
    sighted_at      TIMESTAMP   NOT NULL,
    city            VARCHAR(50) NOT NULL,
    region          VARCHAR(50) NOT NULL,
    latitude        DOUBLE PRECISION NOT NULL,
    longitude       DOUBLE PRECISION NOT NULL,
    species         VARCHAR(50) NOT NULL,
    latin_name      VARCHAR(80) NOT NULL,
    notes           VARCHAR(500),
    image_filename  VARCHAR(100),
    source          VARCHAR(20) NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX idx_sightings_sighted_at ON sightings (sighted_at);
CREATE INDEX idx_sightings_city       ON sightings (city);
CREATE INDEX idx_sightings_region     ON sightings (region);
CREATE INDEX idx_sightings_species    ON sightings (species);

CREATE TABLE sync_runs (
    id            BIGSERIAL PRIMARY KEY,
    trigger_type  VARCHAR(20) NOT NULL,
    status        VARCHAR(20) NOT NULL,
    started_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    finished_at   TIMESTAMP WITH TIME ZONE,
    fetched       INTEGER NOT NULL DEFAULT 0,
    inserted      INTEGER NOT NULL DEFAULT 0,
    duplicates    INTEGER NOT NULL DEFAULT 0,
    rejected      INTEGER NOT NULL DEFAULT 0,
    corrected     INTEGER NOT NULL DEFAULT 0,
    error_message VARCHAR(1000)
);

CREATE INDEX idx_sync_runs_started_at ON sync_runs (started_at);

CREATE TABLE rejected_records (
    id           BIGSERIAL PRIMARY KEY,
    sync_run_id  BIGINT NOT NULL REFERENCES sync_runs (id),
    external_id  VARCHAR(64),
    raw_json     TEXT NOT NULL,
    reason       VARCHAR(500) NOT NULL,
    rejected_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX idx_rejected_records_sync_run ON rejected_records (sync_run_id);
