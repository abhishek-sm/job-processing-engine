package com.abhi.jobengine.exception;

import java.util.UUID;

/** No job exists with the requested id. Mapped to HTTP 404. */
public class JobNotFoundException extends RuntimeException {

    public JobNotFoundException(UUID id) {
        super("Job " + id + " not found");
    }
}
