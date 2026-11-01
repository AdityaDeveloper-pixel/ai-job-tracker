package com.jobtracker.jobservice.exception;

/**
 * Thrown when a requested resource (job application, campaign, recipient) is not found.
 * Handled globally by {@link GlobalExceptionHandler} to return a 404 page or response.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public ResourceNotFoundException(String resourceName, Long id) {
        super(resourceName + " not found with id: " + id);
    }
}
