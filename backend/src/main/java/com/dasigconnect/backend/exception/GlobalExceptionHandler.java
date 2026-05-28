package com.dasigconnect.backend.exception;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatus(ResponseStatusException ex) {
        String message = ex.getReason() != null ? ex.getReason() : ex.getMessage();
        return ResponseEntity.status(ex.getStatusCode())
                .body(Map.of("error", message, "status", ex.getStatusCode().value()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(fe.getField(), fe.getDefaultMessage());
        }
        return ResponseEntity.badRequest()
                .body(Map.of("error", "Validation failed", "status", 400, "fields", fieldErrors));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, Object>> handleMissingRequestParameter(
            MissingServletRequestParameterException ex) {
        return ResponseEntity.badRequest()
                .body(Map.of(
                        "error", "Missing required request parameter: " + ex.getParameterName(),
                        "status", 400));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.badRequest()
                .body(Map.of("error", ex.getMessage(), "status", 400));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalState(IllegalStateException ex) {
        log.error("Upstream/storage failure", ex);
        String message = ex.getMessage() != null ? ex.getMessage() : "Upstream service error";
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(Map.of("error", message, "status", 502));
    }

    @ExceptionHandler(CannotCreateTransactionException.class)
    public ResponseEntity<Map<String, Object>> handleConnectionPoolExhaustion(CannotCreateTransactionException ex) {
        log.error("Database connection pool exhausted or unavailable", ex);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("error", "Database is temporarily busy. Please try again.", "status", 503));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDataIntegrity(DataIntegrityViolationException ex) {
        return ResponseEntity.status(409)
                .body(Map.of("error", "Duplicate or invalid data", "status", 409));
    }

    @ExceptionHandler(SlotAlreadyTakenException.class)
    public ResponseEntity<Map<String, Object>> handleSlotAlreadyTaken(SlotAlreadyTakenException ex) {
        return ResponseEntity.status(409)
                .body(Map.of("error", ex.getMessage(), "status", 409));
    }

    @ExceptionHandler(GuardRailViolationException.class)
    public ResponseEntity<Map<String, Object>> handleGuardRailViolation(GuardRailViolationException ex) {
        String message = ex.getViolations().isEmpty()
                ? ex.getMessage()
                : ex.getViolations().get(0).getMessage();
        return ResponseEntity.status(422)
                .body(Map.of(
                        "error", message,
                        "summary", ex.getMessage(),
                        "status", 422,
                        "violations", ex.getViolations()));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNoResourceFound(NoResourceFoundException ex) {
        return ResponseEntity.status(404)
                .body(Map.of("error", "Not found", "status", 404));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex,
            HttpServletRequest request) {
        String[] supported = ex.getSupportedMethods();
        log.warn("Method not allowed: {} {} (supported: {})",
                request.getMethod(), request.getRequestURI(),
                supported != null ? Arrays.toString(supported) : "none");
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(Map.of(
                "error", "Method not allowed",
                "method", request.getMethod(),
                "path", request.getRequestURI(),
                "supportedMethods", supported != null ? supported : new String[0],
                "status", 405));
    }

    @ExceptionHandler(AsyncRequestTimeoutException.class)
    public void handleAsyncTimeout(AsyncRequestTimeoutException ex, HttpServletResponse response) {
        // Expected when an SSE stream's timeout fires. Response is already committed
        // so no body can be written — just absorb silently at debug level.
        if (!response.isCommitted()) {
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        }
        log.debug("Async request timed out (SSE stream expired)");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public void handleAccessDenied(AccessDeniedException ex) throws AccessDeniedException {
        throw ex;
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.internalServerError()
                .body(Map.of("error", "Internal server error", "status", 500));
    }
}
