package com.example.frenchlearning.common.api;

import com.example.frenchlearning.configuration.ApplicationProperties;
import com.example.frenchlearning.exception.ApiException;
import com.example.frenchlearning.exception.ErrorCode;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public final class PageRequestValidator {

    private final int defaultPageSize;
    private final int maximumPageSize;

    @Autowired
    public PageRequestValidator(ApplicationProperties properties) {
        this(Objects.requireNonNull(properties, "properties must not be null").pagination());
    }

    public PageRequestValidator(ApplicationProperties.Pagination pagination) {
        Objects.requireNonNull(pagination, "pagination must not be null");
        this.defaultPageSize = pagination.defaultPageSize();
        this.maximumPageSize = pagination.maximumPageSize();
    }

    public PageRequest validate(Integer page, Integer size) {
        int resolvedPage = page == null ? 0 : page;
        int resolvedSize = size == null ? defaultPageSize : size;
        if (resolvedPage < 0 || resolvedSize < 1 || resolvedSize > maximumPageSize) {
            throw invalidPagination();
        }
        return PageRequest.of(resolvedPage, resolvedSize);
    }

    private ApiException invalidPagination() {
        return new ApiException(
                HttpStatus.BAD_REQUEST,
                ErrorCode.VALIDATION_FAILED,
                "Validation Failed",
                "Pagination parameters are invalid");
    }
}
