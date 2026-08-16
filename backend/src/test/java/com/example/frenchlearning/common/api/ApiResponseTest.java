package com.example.frenchlearning.common.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ApiResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void serializesDataWithoutNullMetadata() {
        ApiResponse<Map<String, String>> response =
                ApiResponse.of(Map.of("status", "available"));

        assertThat(json(response))
                .isEqualTo("{\"data\":{\"status\":\"available\"}}");
    }

    @Test
    void serializesPageMetadataUsingTheContractFieldNames() {
        ApiResponse<List<String>> response =
                ApiResponse.of(List.of("lesson-1", "lesson-2"), new PageMeta(0, 20, 42, 3));

        assertThat(json(response))
                .isEqualTo(
                        "{\"data\":[\"lesson-1\",\"lesson-2\"],\"meta\":"
                                + "{\"page\":0,\"size\":20,\"totalElements\":42,\"totalPages\":3}}");
    }

    @Test
    void serializesCursorMetadataAndOmitsAnAbsentNextCursor() {
        ApiResponse<List<String>> finalPage =
                ApiResponse.of(List.of("attempt-1"), new CursorMeta(20, false, null));
        ApiResponse<List<String>> continuedPage =
                ApiResponse.of(List.of("attempt-2"), new CursorMeta(20, true, "opaque-cursor"));

        assertThat(json(finalPage))
                .isEqualTo(
                        "{\"data\":[\"attempt-1\"],\"meta\":{\"size\":20,\"hasMore\":false}}");
        assertThat(json(continuedPage))
                .isEqualTo(
                        "{\"data\":[\"attempt-2\"],\"meta\":{\"size\":20,\"hasMore\":true,"
                                + "\"nextCursor\":\"opaque-cursor\"}}");
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new AssertionError("Response must serialize", exception);
        }
    }
}
