package com.stoxsim.common.error;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import io.micrometer.core.instrument.MeterRegistry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApiExceptionHandler.class);
    private final MeterRegistry meterRegistry;

    public ApiExceptionHandler(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @ExceptionHandler(ConflictException.class)
    ResponseEntity<ApiError> handleConflict(ConflictException exception) {
        recordError(HttpStatus.CONFLICT, "conflict");
        return response(HttpStatus.CONFLICT, exception.getMessage(), Map.of());
    }

    @ExceptionHandler(UnauthorizedException.class)
    ResponseEntity<ApiError> handleUnauthorized(UnauthorizedException exception) {
        recordError(HttpStatus.UNAUTHORIZED, "unauthorized");
        return response(HttpStatus.UNAUTHORIZED, exception.getMessage(), Map.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException exception) {
        Map<String, String> fields = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors()
            .forEach(error -> fields.putIfAbsent(error.getField(), error.getDefaultMessage()));
        recordError(HttpStatus.BAD_REQUEST, "validation");
        return response(HttpStatus.BAD_REQUEST, "Request validation failed", fields);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> handleUnexpected(Exception exception) {
        recordError(HttpStatus.INTERNAL_SERVER_ERROR, "unhandled");
        LOGGER.error(
            "Unhandled API exception: type={}",
            exception.getClass().getName()
        );
        return response(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "An unexpected error occurred. Use the X-Request-ID response header when contacting support.",
            Map.of()
        );
    }

    private void recordError(HttpStatus status, String category) {
        meterRegistry.counter(
            "stoxsim.api.errors",
            "status",
            String.valueOf(status.value()),
            "category",
            category
        ).increment();
    }

    private ResponseEntity<ApiError> response(
        HttpStatus status,
        String message,
        Map<String, String> fields
    ) {
        return ResponseEntity.status(status)
            .body(new ApiError(Instant.now(), status.value(), status.getReasonPhrase(), message, fields));
    }

    record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        Map<String, String> fields
    ) {
    }
}
