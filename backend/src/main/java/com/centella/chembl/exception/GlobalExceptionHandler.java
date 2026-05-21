package com.centella.chembl.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.Map;

/**
 * Global exception handler — ensures all errors return a consistent JSON structure
 * with an appropriate HTTP status code and human-readable message.
 *
 * Error response shape:
 * {
 *   "status": "error",
 *   "code": "TARGET_NOT_FOUND",
 *   "message": "...",
 *   "timestamp": "2026-05-21T10:00:00Z"
 * }
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(InvalidTargetIdException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidTargetId(InvalidTargetIdException ex) {
        log.warn("Invalid target ID: {}", ex.getMessage());
        return error(HttpStatus.BAD_REQUEST, "INVALID_TARGET_ID", ex.getMessage());
    }

    @ExceptionHandler(TargetNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleTargetNotFound(TargetNotFoundException ex) {
        log.warn("Target not found: {}", ex.getMessage());
        return error(HttpStatus.NOT_FOUND, "TARGET_NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(ChemblApiException.class)
    public ResponseEntity<Map<String, Object>> handleChemblApiError(ChemblApiException ex) {
        log.error("ChEMBL API error: {}", ex.getMessage());
        return error(HttpStatus.BAD_GATEWAY, "CHEMBL_API_ERROR", ex.getMessage());
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, Object>> handleMissingParam(MissingServletRequestParameterException ex) {
        return error(HttpStatus.BAD_REQUEST, "MISSING_PARAMETER",
                "Required parameter '" + ex.getParameterName() + "' is missing.");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneral(Exception ex) {
        log.error("Unexpected error", ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "An unexpected error occurred. Please try again.");
    }

    private ResponseEntity<Map<String, Object>> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(Map.of(
                "status", "error",
                "code", code,
                "message", message,
                "timestamp", Instant.now().toString()
        ));
    }
}
