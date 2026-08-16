package com.example.frenchlearning.exception;

import org.springframework.http.HttpStatus;

public class ResourceNotFoundException extends ApiException {

    public ResourceNotFoundException(String detail) {
        super(
                HttpStatus.NOT_FOUND,
                ErrorCode.RESOURCE_NOT_FOUND,
                "Resource Not Found",
                detail);
    }
}
