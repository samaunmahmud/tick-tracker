package com.samaun.ticktracker.common;

import java.time.Instant;
import java.util.List;

public record ApiError(
        int status,
        String error,
        String message,
        List<FieldError> fieldErrors,
        Instant timestamp
) {
    public record FieldError(String field, String message) {}

    public static ApiError of(int status, String error, String message) {
        return new ApiError(status, error, message, List.of(), Instant.now());
    }

    public static ApiError withFields(int status, String error, String message, List<FieldError> fieldErrors) {
        return new ApiError(status, error, message, fieldErrors, Instant.now());
    }
}
