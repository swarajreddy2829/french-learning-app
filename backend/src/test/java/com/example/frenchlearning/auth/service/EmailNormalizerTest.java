package com.example.frenchlearning.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.example.frenchlearning.exception.ApiException;
import com.example.frenchlearning.exception.ErrorCode;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class EmailNormalizerTest {

    private static final Locale TURKISH = Locale.forLanguageTag("tr-TR");

    private final EmailNormalizer emailNormalizer = new EmailNormalizer();

    private Locale previousLocale;

    @BeforeEach
    void rememberDefaultLocale() {
        previousLocale = Locale.getDefault();
    }

    @AfterEach
    void restoreDefaultLocale() {
        Locale.setDefault(previousLocale);
    }

    @Test
    void trimsSurroundingWhitespaceAndLowercasesForLookupWithoutChangingDisplayCasing() {
        String supplied = " Learner@Example.Test ";

        assertThat(emailNormalizer.toDisplayForm(supplied)).isEqualTo("Learner@Example.Test");
        assertThat(emailNormalizer.normalize(supplied)).isEqualTo("learner@example.test");
    }

    @Test
    void doesNotApplyProviderSpecificRewrites() {
        String gmailStyle = "A.B+tag@Gmail.COM";

        assertThat(emailNormalizer.toDisplayForm(gmailStyle)).isEqualTo("A.B+tag@Gmail.COM");
        assertThat(emailNormalizer.normalize(gmailStyle)).isEqualTo("a.b+tag@gmail.com");
    }

    @Test
    void lowercasesIndependentlyOfATurkishDefaultLocale() {
        Locale.setDefault(TURKISH);

        assertThat("I".toLowerCase(TURKISH)).isEqualTo("ı");
        assertThat(emailNormalizer.normalize("  I@Example.TEST  ")).isEqualTo("i@example.test");
        assertThat(emailNormalizer.normalize("LEARNER@EXAMPLE.TEST"))
                .isEqualTo("learner@example.test");
        assertThat(emailNormalizer.toDisplayForm(" I@Example.TEST ")).isEqualTo("I@Example.TEST");
    }

    @Test
    void rejectsNullAndBlankEmailsWithoutEchoingTheInput() {
        assertInvalid(null, "null");
        assertInvalid("   ", "   ");
        assertInvalid("\n\t", "\n\t");
    }

    private void assertInvalid(String email, String rejectedValue) {
        assertThatExceptionOfType(ApiException.class)
                .isThrownBy(() -> emailNormalizer.normalize(email))
                .satisfies(exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
                    assertThat(exception.getTitle()).isEqualTo("Validation Failed");
                    assertThat(exception.getMessage()).isEqualTo("Email is required");
                    if (rejectedValue != null && !rejectedValue.isBlank()) {
                        assertThat(exception.getMessage()).doesNotContain(rejectedValue);
                        assertThat(exception.toString()).doesNotContain(rejectedValue);
                    }
                });
    }
}
