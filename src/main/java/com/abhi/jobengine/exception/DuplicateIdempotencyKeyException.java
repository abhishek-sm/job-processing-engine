package com.abhi.jobengine.exception;

/** The tenant already submitted a job with this idempotency key. Mapped to HTTP 409. */
public class DuplicateIdempotencyKeyException extends RuntimeException {

    public DuplicateIdempotencyKeyException(String tenantId, String idempotencyKey, Throwable cause) {
        super("A job with idempotency key '" + idempotencyKey + "' already exists for tenant '"
                + tenantId + "'", cause);
    }
}
