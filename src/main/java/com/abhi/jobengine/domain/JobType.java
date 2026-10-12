package com.abhi.jobengine.domain;

/**
 * Kind of work a {@link Job} performs. Each type will be handled by a dedicated executor.
 *
 * <p>Persisted by name ({@code EnumType.STRING}) in {@code jobs.job_type}. The column has no
 * check constraint, so adding a type only requires a new constant here (plus its executor).
 * Never rename or remove a constant that may already be stored.
 */
public enum JobType {

    /** Builds a tenant-specific report from the parameters in the payload. */
    GENERATE_REPORT,

    /** Applies bulk updates to a tenant's customer records. */
    UPDATE_CUSTOMERS,

    /** Matches a tenant's transactions against an external or ledger source. */
    RECONCILE_TRANSACTIONS,

    /** Rolls up a tenant's raw data into daily metrics. */
    AGGREGATE_DAILY_METRICS
}
