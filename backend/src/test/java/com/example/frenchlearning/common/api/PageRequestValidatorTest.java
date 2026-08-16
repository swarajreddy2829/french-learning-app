package com.example.frenchlearning.common.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.example.frenchlearning.configuration.ApplicationProperties;
import com.example.frenchlearning.exception.ApiException;
import com.example.frenchlearning.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;

class PageRequestValidatorTest {

    private final PageRequestValidator validator = new PageRequestValidator(
            new ApplicationProperties.Pagination(20, 100));

    @Test
    void appliesDefaultsAndAcceptsMinimumNormalAndMaximumValues() {
        assertPage(validator.validate(null, null), 0, 20);
        assertPage(validator.validate(0, 1), 0, 1);
        assertPage(validator.validate(3, 25), 3, 25);
        assertPage(validator.validate(8, 100), 8, 100);
    }

    @Test
    void usesTheConfiguredMaximumInsteadOfAHardcodedLimit() {
        PageRequestValidator configuredValidator = new PageRequestValidator(
                new ApplicationProperties.Pagination(10, 37));

        assertPage(configuredValidator.validate(0, 37), 0, 37);
        assertInvalid(() -> configuredValidator.validate(0, 38));
    }

    @Test
    void rejectsNegativePagesAndOutOfRangeSizesPredictably() {
        assertInvalid(() -> validator.validate(-1, 20));
        assertInvalid(() -> validator.validate(0, 0));
        assertInvalid(() -> validator.validate(0, 101));
    }

    private void assertPage(PageRequest request, int expectedPage, int expectedSize) {
        assertThat(request.getPageNumber()).isEqualTo(expectedPage);
        assertThat(request.getPageSize()).isEqualTo(expectedSize);
    }

    private void assertInvalid(Runnable action) {
        assertThatExceptionOfType(ApiException.class)
                .isThrownBy(action::run)
                .satisfies(exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
                    assertThat(exception.getMessage())
                            .isEqualTo("Pagination parameters are invalid");
                });
    }
}
