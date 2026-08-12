CREATE TABLE jobs (
    id             UUID PRIMARY KEY,
    name           VARCHAR(120) NOT NULL,
    status         VARCHAR(32)  NOT NULL,
    payload        TEXT,
    result         TEXT,
    error          TEXT,
    attempt_count  INT          NOT NULL DEFAULT 0,
    max_attempts   INT          NOT NULL DEFAULT 3,
    created_at     TIMESTAMPTZ  NOT NULL,
    started_at     TIMESTAMPTZ,
    completed_at   TIMESTAMPTZ,
    updated_at     TIMESTAMPTZ  NOT NULL
);

CREATE UNIQUE INDEX uk_jobs_name ON jobs (LOWER(name));
CREATE INDEX idx_jobs_status_created_at ON jobs (status, created_at);

CREATE TABLE dead_letter_jobs (
    id              UUID PRIMARY KEY,
    original_job_id UUID         NOT NULL,
    name            VARCHAR(120) NOT NULL,
    payload         TEXT,
    error           TEXT,
    attempts        INT          NOT NULL,
    failed_at       TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_dead_letter_jobs_failed_at ON dead_letter_jobs (failed_at DESC);
