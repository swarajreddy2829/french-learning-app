package com.example.frenchlearning.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CursorMeta(int size, boolean hasMore, String nextCursor) {

    public CursorMeta {
        if (size < 1) {
            throw new IllegalArgumentException("size must be at least 1");
        }
        if (nextCursor != null && nextCursor.isBlank()) {
            throw new IllegalArgumentException("nextCursor must not be blank");
        }
        if (hasMore && nextCursor == null) {
            throw new IllegalArgumentException("nextCursor is required when more results exist");
        }
    }
}
