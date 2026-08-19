package com.example.frenchlearning.auth.service;

import com.example.frenchlearning.exception.ApiException;
import com.example.frenchlearning.exception.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Enforces the documented registration password length policy.
 *
 * <p>This component validates candidate passwords only. It must not hash, persist, log, or include
 * password values in exception details.
 */
@Component
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 12;
    public static final int MAX_LENGTH = 128;

    public void validate(String password) {
        if (password == null || !hasAllowedLength(password)) {
            throw invalidPassword();
        }
    }

    private static boolean hasAllowedLength(String password) {
        int length = password.codePointCount(0, password.length());
        return length >= MIN_LENGTH && length <= MAX_LENGTH;
    }

    private static ApiException invalidPassword() {
        return new ApiException(
                HttpStatus.BAD_REQUEST,
                ErrorCode.VALIDATION_FAILED,
                "Validation Failed",
                "Password does not meet the required policy");
    }
}
