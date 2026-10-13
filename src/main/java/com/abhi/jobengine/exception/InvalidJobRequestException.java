package com.abhi.jobengine.exception;

/**
 * Client input that passed bean validation but breaks a business rule, such as a payload that
 * does not match the job type or an unsupported sort field. Mapped to HTTP 400.
 */
public class InvalidJobRequestException extends RuntimeException {

    public InvalidJobRequestException(String message) {
        super(message);
    }
}
