package com.jobtracker.jobservice.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;

/**
 * Centralised exception handler for the entire application.
 *
 * - Thymeleaf routes ΓåÆ render user-friendly error HTML pages
 * - REST /api/** routes ΓåÆ return structured JSON error responses
 */
@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // ---- Resource Not Found (404) ----

    @ExceptionHandler(ResourceNotFoundException.class)
    public Object handleNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        log.warn("Resource not found: {} ΓÇö URI: {}", ex.getMessage(), request.getRequestURI());

        if (isApiRequest(request)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of(
                            "status", 404,
                            "error", "Not Found",
                            "message", ex.getMessage(),
                            "path", request.getRequestURI()
                    ));
        }

        ModelAndView mav = new ModelAndView("error/404");
        mav.setStatus(HttpStatus.NOT_FOUND);
        mav.addObject("message", ex.getMessage());
        return mav;
    }

    // ---- AI Service Failure ----

    @ExceptionHandler(AiServiceException.class)
    public Object handleAiServiceError(AiServiceException ex, HttpServletRequest request) {
        log.error("AI service error: {} ΓÇö URI: {}", ex.getMessage(), request.getRequestURI(), ex);

        if (isApiRequest(request)) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of(
                            "status", 503,
                            "error", "AI Service Unavailable",
                            "message", ex.getMessage(),
                            "path", request.getRequestURI()
                    ));
        }

        ModelAndView mav = new ModelAndView("error/500");
        mav.setStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        mav.addObject("message", "AI service is temporarily unavailable. Your data has been saved ΓÇö AI analysis will be skipped.");
        return mav;
    }

    // ---- Email Send Failure ----

    @ExceptionHandler(EmailSendException.class)
    public Object handleEmailError(EmailSendException ex, HttpServletRequest request) {
        log.error("Email send error: {} ΓÇö URI: {}", ex.getMessage(), request.getRequestURI(), ex);

        if (isApiRequest(request)) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "status", 500,
                            "error", "Email Send Failed",
                            "message", ex.getMessage(),
                            "path", request.getRequestURI()
                    ));
        }

        ModelAndView mav = new ModelAndView("error/500");
        mav.setStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        mav.addObject("message", "Failed to send email: " + ex.getMessage());
        return mav;
    }

    // ---- Spring's own 404 for static resources / unmapped URLs ----

    @ExceptionHandler(NoResourceFoundException.class)
    public Object handleSpring404(NoResourceFoundException ex, HttpServletRequest request) {
        log.warn("No resource found: {}", request.getRequestURI());

        if (isApiRequest(request)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of(
                            "status", 404,
                            "error", "Not Found",
                            "message", "The requested URL was not found.",
                            "path", request.getRequestURI()
                    ));
        }

        ModelAndView mav = new ModelAndView("error/404");
        mav.setStatus(HttpStatus.NOT_FOUND);
        mav.addObject("message", "Page not found.");
        return mav;
    }

    // ---- Catch-All (500) ----

    @ExceptionHandler(Exception.class)
    public Object handleGenericError(Exception ex, HttpServletRequest request) {
        log.error("Unexpected error ΓÇö URI: {}", request.getRequestURI(), ex);

        if (isApiRequest(request)) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "status", 500,
                            "error", "Internal Server Error",
                            "message", "An unexpected error occurred. Please try again.",
                            "path", request.getRequestURI()
                    ));
        }

        ModelAndView mav = new ModelAndView("error/500");
        mav.setStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        mav.addObject("message", "Something went wrong. Please try again.");
        return mav;
    }

    // ---- Helper ----

    private boolean isApiRequest(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/api/");
    }
}
