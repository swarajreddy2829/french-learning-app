package com.example.frenchlearning.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(T data, Object meta) {

    public ApiResponse {
        Objects.requireNonNull(data, "data must not be null");
    }

    public static <T> ApiResponse<T> of(T data) {
        return new ApiResponse<>(data, null);
    }

    public static <T> ApiResponse<T> of(T data, Object meta) {
        return new ApiResponse<>(data, Objects.requireNonNull(meta, "meta must not be null"));
    }
}
