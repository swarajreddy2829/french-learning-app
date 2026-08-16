package com.example.frenchlearning.exception;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public enum ErrorCode {
    VALIDATION_FAILED("VALIDATION_FAILED"),
    MALFORMED_REQUEST("MALFORMED_REQUEST"),
    AUTHENTICATION_REQUIRED("AUTHENTICATION_REQUIRED"),
    AUTHENTICATION_FAILED("AUTHENTICATION_FAILED"),
    MALFORMED_TOKEN("MALFORMED_TOKEN"),
    EXPIRED_TOKEN("EXPIRED_TOKEN"),
    ACCESS_DENIED("ACCESS_DENIED"),
    RESOURCE_NOT_FOUND("RESOURCE_NOT_FOUND"),
    CONFLICT("CONFLICT"),
    EMAIL_ALREADY_REGISTERED("EMAIL_ALREADY_REGISTERED"),
    RATE_LIMIT_EXCEEDED("RATE_LIMIT_EXCEEDED"),
    INTERNAL_ERROR("INTERNAL_ERROR");

    public static final String CODE_EXTENSION = "code";
    public static final String TRACE_ID_EXTENSION = "traceId";
    public static final String TIMESTAMP_EXTENSION = "timestamp";
    public static final String FIELD_ERRORS_EXTENSION = "fieldErrors";

    private static final Map<String, ErrorCode> VALUES =
            Arrays.stream(values())
                    .collect(Collectors.toUnmodifiableMap(ErrorCode::value, Function.identity()));

    private final String value;

    ErrorCode(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    @JsonCreator
    public static ErrorCode fromValue(String value) {
        ErrorCode errorCode = VALUES.get(value);
        if (errorCode == null) {
            throw new IllegalArgumentException("Unknown error code: " + value);
        }
        return errorCode;
    }
}
