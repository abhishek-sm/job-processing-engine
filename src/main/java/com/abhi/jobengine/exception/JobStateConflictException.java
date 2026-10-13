package com.abhi.jobengine.exception;

/**
 * The job's current status does not allow the requested change, for example editing or
 * deleting a RUNNING job. Mapped to HTTP 409.
 */
public class JobStateConflictException extends RuntimeException {

    public JobStateConflictException(String message) {
        super(message);
    }
}
