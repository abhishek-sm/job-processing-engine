package com.abhi.jobengine.domain;

/**
 * Lifecycle state of a {@link Job}.
 *
 * <p>Persisted by name ({@code EnumType.STRING}) in {@code jobs.job_status}. The database
 * check constraint {@code ck_jobs_job_status} lists the same values, so adding a status
 * requires a new migration.
 */
public enum JobStatus {

    /** Waiting to be picked up by a worker. */
    PENDING,

    /** Claimed by a worker and currently executing. */
    RUNNING,

    /** Failed with a retryable error; eligible again once {@code nextRunAt} has passed. */
    RETRY_WAIT,

    /** Completed successfully. Terminal. */
    SUCCEEDED,

    /** Exhausted its attempts or failed with a non-retryable error. Terminal. */
    FAILED,

    /** Cancelled before completion. Terminal. */
    CANCELLED;

    /** Whether no further state transitions are expected. */
    public boolean isTerminal() {
        return this == SUCCEEDED || this == FAILED || this == CANCELLED;
    }
}
