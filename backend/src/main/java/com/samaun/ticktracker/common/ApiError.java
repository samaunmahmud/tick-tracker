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
}
