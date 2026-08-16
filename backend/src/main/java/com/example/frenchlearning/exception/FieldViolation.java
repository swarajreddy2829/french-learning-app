package com.example.frenchlearning.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record FieldViolation(String field, String code, String message, Object rejectedValue) {

    public FieldViolation {
        field = requireText(field, "field");
        code = requireText(code, "code");
        message = requireText(message, "message");
    }

    public static FieldViolation of(String field, String code, String message) {
        return new FieldViolation(field, code, message, null);
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
