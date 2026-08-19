package com.example.frenchlearning.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.example.frenchlearning.exception.ApiException;
import com.example.frenchlearning.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class PasswordPolicyTest {

    private final PasswordPolicy passwordPolicy = new PasswordPolicy();

    @Test
    void acceptsDocumentedValidAndBoundaryLengths() {
        assertThatCode(() -> passwordPolicy.validate("Learner-local-123!")).doesNotThrowAnyException();
        assertThatCode(() -> passwordPolicy.validate("a".repeat(PasswordPolicy.MIN_LENGTH)))
                .doesNotThrowAnyException();
        assertThatCode(() -> passwordPolicy.validate("a".repeat(PasswordPolicy.MAX_LENGTH)))
                .doesNotThrowAnyException();
        assertThatCode(() -> passwordPolicy.validate("😀".repeat(PasswordPolicy.MIN_LENGTH)))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsNullEmptyAndOutOfRangePasswordsWithoutLeakingTheValue() {
        assertInvalid(null, "null-password");
        assertInvalid("", "");
        assertInvalid("short-11!!", "short-11!!");
        assertInvalid("a".repeat(PasswordPolicy.MIN_LENGTH - 1), "a".repeat(PasswordPolicy.MIN_LENGTH - 1));
        assertInvalid("a".repeat(PasswordPolicy.MAX_LENGTH + 1), "a".repeat(PasswordPolicy.MAX_LENGTH + 1));
        assertInvalid("😀".repeat(PasswordPolicy.MIN_LENGTH - 1), "😀".repeat(PasswordPolicy.MIN_LENGTH - 1));
        assertInvalid("LeakSecret!", "LeakSecret!");
    }

    private void assertInvalid(String password, String rejectedValue) {
        assertThatExceptionOfType(ApiException.class)
                .isThrownBy(() -> passwordPolicy.validate(password))
                .satisfies(exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
                    assertThat(exception.getTitle()).isEqualTo("Validation Failed");
                    assertThat(exception.getMessage())
                            .isEqualTo("Password does not meet the required policy");
                    if (rejectedValue != null && !rejectedValue.isBlank()) {
                        assertThat(exception.getMessage()).doesNotContain(rejectedValue);
                        assertThat(exception.toString()).doesNotContain(rejectedValue);
                    }
                });
    }
}
