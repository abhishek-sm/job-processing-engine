-- Jobs submitted by tenants. Mirrors com.abhi.jobengine.domain.Job.
-- gen_random_uuid() is built into PostgreSQL 13+, no extension needed.
CREATE TABLE jobs (
    id              UUID         NOT NULL DEFAULT gen_random_uuid(),
    tenant_id       VARCHAR(100) NOT NULL,
    job_status      VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    job_type        VARCHAR(50)  NOT NULL,
    payload         JSONB        NOT NULL,
    priority        INTEGER      NOT NULL DEFAULT 0,
    attempt_count   INTEGER      NOT NULL DEFAULT 0,
    max_attempts    INTEGER      NOT NULL DEFAULT 3,
    next_run_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    locked_by       VARCHAR(255),
    lease_until     TIMESTAMPTZ,
    heartbeat       TIMESTAMPTZ,
    fencing_token   BIGINT       NOT NULL DEFAULT 0,
    idempotency_key VARCHAR(255),
    error_message   TEXT,
    started_at      TIMESTAMPTZ,
    completed_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT pk_jobs PRIMARY KEY (id),
    CONSTRAINT ck_jobs_job_status CHECK (job_status IN
        ('PENDING', 'RUNNING', 'RETRY_WAIT', 'SUCCEEDED', 'FAILED', 'CANCELLED')),
    CONSTRAINT ck_jobs_attempts CHECK (
        attempt_count >= 0 AND max_attempts >= 1 AND attempt_count <= max_attempts),
    CONSTRAINT ck_jobs_fencing_token CHECK (fencing_token >= 0)
);

-- Idempotent submission: one job per (tenant, key) when a key is supplied.
CREATE UNIQUE INDEX ux_jobs_tenant_idempotency_key
    ON jobs (tenant_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;

-- Dispatcher polling: walk runnable jobs in dispatch order
-- (priority DESC, next_run_at, created_at, id) and filter on next_run_at <= now().
-- Partial, so finished jobs never bloat it.
CREATE INDEX ix_jobs_runnable
    ON jobs (priority DESC, next_run_at, created_at, id)
    WHERE job_status IN ('PENDING', 'RETRY_WAIT');

-- Tenant-scoped queries (list a tenant's jobs, check whether a tenant has a RUNNING job).
CREATE INDEX ix_jobs_tenant_status_created
    ON jobs (tenant_id, job_status, created_at);
