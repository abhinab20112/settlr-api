package com.settlr.settlr_api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * Standardised error envelope returned by GlobalExceptionHandler.
 * All error responses across the API share this shape.
 *
 * The optional `fieldErrors` list is included only for validation failures,
 * giving clients per-field detail for form rendering.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)   // hide null fields (e.g. fieldErrors on non-422s)
public record ErrorResponse(
        int status,
        String error,
        String message,
        String path,
        Instant timestamp,
        List<FieldValidationError> fieldErrors
) {
    /** Convenience factory — no field errors. */
    public static ErrorResponse of(int status, String error, String message, String path) {
        return new ErrorResponse(status, error, message, path, Instant.now(), null);
    }

    /** Convenience factory — with per-field validation errors. */
    public static ErrorResponse of(int status, String error, String message, String path,
                                   List<FieldValidationError> fieldErrors) {
        return new ErrorResponse(status, error, message, path, Instant.now(), fieldErrors);
    }

    /**
     * Represents a single field-level validation failure.
     */
    public record FieldValidationError(
            String field,
            Object rejectedValue,
            String message
    ) {}
}
