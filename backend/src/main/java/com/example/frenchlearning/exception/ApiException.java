package com.example.frenchlearning.exception;

import java.util.Objects;
import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final ErrorCode errorCode;
    private final String title;

    public ApiException(HttpStatus status, ErrorCode errorCode, String title, String detail) {
        super(requireText(detail, "detail"));
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode must not be null");
        this.title = requireText(title, "title");
    }

    public HttpStatus getStatus() {
        return status;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public String getTitle() {
        return title;
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
