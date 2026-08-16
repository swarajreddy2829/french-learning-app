package com.example.frenchlearning.exception;

import org.springframework.http.HttpStatus;

public class ConflictException extends ApiException {

    public ConflictException(String detail) {
        this(ErrorCode.CONFLICT, detail);
    }

    public ConflictException(ErrorCode errorCode, String detail) {
        super(HttpStatus.CONFLICT, errorCode, "Conflict", detail);
    }
}
