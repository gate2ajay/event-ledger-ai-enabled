package com.ledger.account.exception;

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

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private final Tracer tracer;

    public GlobalExceptionHandler(Tracer tracer) {
        this.tracer = tracer;
    }

    // 1. Idempotency Hit - return HTTP 209 Conflict
    @ExceptionHandler(DuplicateTransactionException.class)
    public ResponseEntity<ProblemDetail> handleDuplicateTransaction(DuplicateTransactionException ex) {
        log.info("Handling duplicate transaction exception: {}", ex.getMessage());
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                org.springframework.http.HttpStatusCode.valueOf(209), // Custom status code 209 (supported by HttpStatusCode interface)
                ex.getMessage());
        addTraceId(problemDetail);
        return ResponseEntity.status(209).body(problemDetail);
    }

    // 2. Validation Failures - return HTTP 400 Bad Request
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

    // 3. Generic Exceptions - return HTTP 500
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleGenericException(Exception ex) {
        log.error("An unexpected error occurred in the account service", ex);
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
