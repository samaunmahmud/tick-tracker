package com.samaun.ticktracker.common;

import java.util.List;

public class InvalidRequestException extends RuntimeException {

    private final List<ApiError.FieldError> fieldErrors;

    public InvalidRequestException(List<ApiError.FieldError> fieldErrors) {
        super("Validation failed");
        this.fieldErrors = List.copyOf(fieldErrors);
    }

    public List<ApiError.FieldError> getFieldErrors() {
        return fieldErrors;
    }
}
