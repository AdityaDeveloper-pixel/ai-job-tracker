package com.jobtracker.jobservice.exception;

/**
 * Thrown when a call to the external AI agent service fails.
 * Wraps underlying HTTP / connection errors for clean error propagation.
 */
public class AiServiceException extends RuntimeException {

    public AiServiceException(String message) {
        super(message);
    }

    public AiServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
