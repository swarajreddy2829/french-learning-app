package com.example.frenchlearning.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class ErrorModelTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void exposesUniqueContractSafeErrorCodes() {
        assertThat(Arrays.stream(ErrorCode.values()).map(ErrorCode::value))
                .doesNotHaveDuplicates()
                .allMatch(value -> value.matches("^[A-Z][A-Z0-9_]*$"));
        assertThat(json(ErrorCode.EMAIL_ALREADY_REGISTERED))
                .isEqualTo("\"EMAIL_ALREADY_REGISTERED\"");
    }

    @Test
    void definesTheRfc9457ExtensionNames() {
        assertThat(ErrorCode.CODE_EXTENSION).isEqualTo("code");
        assertThat(ErrorCode.TRACE_ID_EXTENSION).isEqualTo("traceId");
        assertThat(ErrorCode.TIMESTAMP_EXTENSION).isEqualTo("timestamp");
        assertThat(ErrorCode.FIELD_ERRORS_EXTENSION).isEqualTo("fieldErrors");
    }

    @Test
    void rejectsUnknownErrorCodesDuringDeserialization() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ErrorCode.fromValue("UNKNOWN_CLIENT_CODE"));
    }

    @Test
    void serializesFieldViolationAndOmitsUnsafeOrAbsentRejectedValue() {
        FieldViolation violation =
                new FieldViolation("email", "NotBlank", "must not be blank", null);

        assertThat(json(violation))
                .isEqualTo(
                        "{\"field\":\"email\",\"code\":\"NotBlank\","
                                + "\"message\":\"must not be blank\"}");
    }

    @Test
    void serializesAnExplicitlySafeRejectedValue() {
        FieldViolation violation =
                new FieldViolation("difficulty", "AllowedValue", "is not supported", "EXPERT");

        assertThat(json(violation))
                .isEqualTo(
                        "{\"field\":\"difficulty\",\"code\":\"AllowedValue\","
                                + "\"message\":\"is not supported\",\"rejectedValue\":\"EXPERT\"}");
    }

    @Test
    void rejectsMissingRequiredFieldViolationValues() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new FieldViolation(null, "NotBlank", "required", null));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new FieldViolation("email", " ", "required", null));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new FieldViolation("email", "NotBlank", "", null));
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new AssertionError("Error model must serialize", exception);
        }
    }
}
