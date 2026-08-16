package com.example.frenchlearning.common.api;

import com.example.frenchlearning.exception.ApiException;
import com.example.frenchlearning.exception.ErrorCode;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public final class AttemptCursorCodec {

    private static final byte FORMAT_VERSION = 1;
    private static final int PAYLOAD_LENGTH =
            Byte.BYTES + Long.BYTES + Integer.BYTES + Long.BYTES;
    private static final int MAX_ENCODED_LENGTH = 500;
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    public String encode(AttemptCursor cursor) {
        Objects.requireNonNull(cursor, "cursor must not be null");
        byte[] payload = ByteBuffer.allocate(PAYLOAD_LENGTH)
                .put(FORMAT_VERSION)
                .putLong(cursor.submittedAt().getEpochSecond())
                .putInt(cursor.submittedAt().getNano())
                .putLong(cursor.attemptId())
                .array();
        return ENCODER.encodeToString(payload);
    }

    public AttemptCursor decode(String encodedCursor) {
        if (encodedCursor == null
                || encodedCursor.isBlank()
                || encodedCursor.length() > MAX_ENCODED_LENGTH) {
            throw invalidCursor();
        }

        try {
            byte[] payload = DECODER.decode(encodedCursor);
            if (payload.length != PAYLOAD_LENGTH
                    || !ENCODER.encodeToString(payload).equals(encodedCursor)) {
                throw invalidCursor();
            }

            ByteBuffer buffer = ByteBuffer.wrap(payload);
            if (buffer.get() != FORMAT_VERSION) {
                throw invalidCursor();
            }

            Instant submittedAt = Instant.ofEpochSecond(buffer.getLong(), buffer.getInt());
            long attemptId = buffer.getLong();
            if (attemptId < 1) {
                throw invalidCursor();
            }
            return new AttemptCursor(submittedAt, attemptId);
        } catch (IllegalArgumentException | BufferUnderflowException | DateTimeException exception) {
            throw invalidCursor();
        }
    }

    private ApiException invalidCursor() {
        return new ApiException(
                HttpStatus.BAD_REQUEST,
                ErrorCode.MALFORMED_REQUEST,
                "Malformed Request",
                "Cursor is invalid");
    }

    public record AttemptCursor(Instant submittedAt, long attemptId) {

        public AttemptCursor {
            Objects.requireNonNull(submittedAt, "submittedAt must not be null");
            if (attemptId < 1) {
                throw new IllegalArgumentException("attemptId must be positive");
            }
        }
    }
}
