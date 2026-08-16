package com.example.frenchlearning.common.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.example.frenchlearning.exception.ApiException;
import com.example.frenchlearning.exception.ErrorCode;
import java.time.Instant;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class AttemptCursorCodecTest {

    private static final Instant SUBMITTED_AT = Instant.parse("2026-08-16T14:42:00.123456789Z");

    private final AttemptCursorCodec codec = new AttemptCursorCodec();

    @Test
    void deterministicallyRoundTripsAnAttemptCursor() {
        AttemptCursorCodec.AttemptCursor cursor =
                new AttemptCursorCodec.AttemptCursor(SUBMITTED_AT, 987654321L);

        String firstEncoding = codec.encode(cursor);
        String secondEncoding = codec.encode(cursor);

        assertThat(firstEncoding)
                .isEqualTo(secondEncoding)
                .matches("^[A-Za-z0-9_-]+$")
                .doesNotContain("=", SUBMITTED_AT.toString(), "987654321");
        assertThat(codec.decode(firstEncoding)).isEqualTo(cursor);
    }

    @Test
    void rejectsMalformedAndInvalidCursorValuesWithOneSafeError() {
        assertInvalid(null);
        assertInvalid("");
        assertInvalid("%%%not-base64%%%");
        assertInvalid(Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[] {1, 2, 3}));

        byte[] invalidIdentifier = Base64.getUrlDecoder().decode(codec.encode(
                new AttemptCursorCodec.AttemptCursor(SUBMITTED_AT, 1)));
        for (int index = invalidIdentifier.length - Long.BYTES;
                index < invalidIdentifier.length;
                index++) {
            invalidIdentifier[index] = 0;
        }
        assertInvalid(Base64.getUrlEncoder().withoutPadding().encodeToString(invalidIdentifier));
    }

    @Test
    void rejectsUnsupportedCursorVersionsWithoutExposingFormatDetails() {
        String encoded =
                codec.encode(new AttemptCursorCodec.AttemptCursor(SUBMITTED_AT, 42));
        byte[] unsupportedVersion = Base64.getUrlDecoder().decode(encoded);
        unsupportedVersion[0] = 99;

        assertInvalid(Base64.getUrlEncoder().withoutPadding().encodeToString(unsupportedVersion));
    }

    private void assertInvalid(String value) {
        assertThatExceptionOfType(ApiException.class)
                .isThrownBy(() -> codec.decode(value))
                .satisfies(exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.MALFORMED_REQUEST);
                    assertThat(exception.getMessage()).isEqualTo("Cursor is invalid");
                    assertThat(exception).hasNoCause();
                });
    }
}
