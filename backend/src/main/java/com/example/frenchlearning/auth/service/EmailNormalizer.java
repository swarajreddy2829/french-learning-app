package com.example.frenchlearning.auth.service;

import com.example.frenchlearning.exception.ApiException;
import com.example.frenchlearning.exception.ErrorCode;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Applies the specified email normalization policy without provider-specific rewriting.
 *
 * <p>Display form is the trimmed original casing. Lookup form is that same value lowercased with
 * {@link Locale#ROOT} so equivalent casing is deterministic regardless of the JVM default locale.
 */
@Component
public final class EmailNormalizer {

    public String toDisplayForm(String email) {
        if (email == null) {
            throw invalidEmail();
        }
        String displayForm = email.strip();
        if (displayForm.isEmpty()) {
            throw invalidEmail();
        }
        return displayForm;
    }

    public String normalize(String email) {
        return toDisplayForm(email).toLowerCase(Locale.ROOT);
    }

    private static ApiException invalidEmail() {
        return new ApiException(
                HttpStatus.BAD_REQUEST,
                ErrorCode.VALIDATION_FAILED,
                "Validation Failed",
                "Email is required");
    }
}
