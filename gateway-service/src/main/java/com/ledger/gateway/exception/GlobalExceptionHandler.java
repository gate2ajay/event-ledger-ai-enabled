package com.ledger.gateway.exception;

import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.micrometer.tracing.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private final Tracer tracer;

    public GlobalExceptionHandler(Tracer tracer) {
        this.tracer = tracer;
    }

    // 1. Idempotency Hit - return original event with HTTP 209 (Conflict) or 200 (OK)
    @ExceptionHandler(DuplicateEventException.class)
    public ResponseEntity<?> handleDuplicateEvent(DuplicateEventException ex) {
        log.info("Handling duplicate event exception for ID: {}", ex.getOriginalEvent().getEventId());
        // Return original event payload with HTTP 209 (Conflict)
        return ResponseEntity.status(209).body(ex.getOriginalEvent());
    }

    // 2. Validation Failures - return HTTP 400 (Bad Request)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidationExceptions(MethodArgumentNotValidException ex) {
        log.warn("Input validation failed: {}", ex.getMessage());
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Input validation failed");
        
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        problemDetail.setProperty("errors", errors);
        addTraceId(problemDetail);
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problemDetail);
    }

    // 3. Circuit Breaker Open - return HTTP 503 (Service Unavailable)
    @ExceptionHandler(CallNotPermittedException.class)
    public ResponseEntity<ProblemDetail> handleCallNotPermittedException(CallNotPermittedException ex) {
        log.error("Circuit breaker is open. Blocking call to Account Service.", ex);
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE, 
                "Account Service is currently unavailable (Circuit Breaker open)");
        addTraceId(problemDetail);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(problemDetail);
    }

    // 4. Bulkhead Full - return HTTP 429 (Too Many Requests)
    @ExceptionHandler(BulkheadFullException.class)
    public ResponseEntity<ProblemDetail> handleBulkheadFullException(BulkheadFullException ex) {
        log.error("Bulkhead is full. Request rejected.", ex);
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.TOO_MANY_REQUESTS, 
                "System is overloaded. Please try again later.");
        addTraceId(problemDetail);
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(problemDetail);
    }

    // 5. Timeout - return HTTP 504 (Gateway Timeout)
    @ExceptionHandler(TimeoutException.class)
    public ResponseEntity<ProblemDetail> handleTimeoutException(TimeoutException ex) {
        log.error("Request to Account Service timed out.", ex);
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.GATEWAY_TIMEOUT, 
                "Account Service call timed out.");
        addTraceId(problemDetail);
        return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT).body(problemDetail);
    }

    // 6. Generic Exceptions - return HTTP 500 (Internal Server Error)
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleGenericException(Exception ex) {
        log.error("An unexpected error occurred in the gateway", ex);
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, 
                "An unexpected internal error occurred. Please refer to trace ID for investigation.");
        addTraceId(problemDetail);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problemDetail);
    }

    private void addTraceId(ProblemDetail problemDetail) {
        String traceId = null;
        if (tracer != null && tracer.currentSpan() != null) {
            traceId = tracer.currentSpan().context().traceId();
        }
        if (traceId == null) {
            traceId = org.slf4j.MDC.get("traceId");
        }
        if (traceId != null) {
            problemDetail.setProperty("trace_id", traceId);
        }
    }
}
