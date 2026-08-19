package com.example.frenchlearning.security;

import com.example.frenchlearning.configuration.CorrelationIdFilter;
import com.example.frenchlearning.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Serializes RFC 9457 security problems for filter-chain authentication and authorization
 * failures using the same field conventions as {@code GlobalExceptionHandler}.
 */
@Component
public final class SecurityProblemWriter {

    static final String PROBLEM_TYPE_PREFIX = "urn:problem-type:french-learning:";

    private final ObjectMapper objectMapper;
    private final Clock clock;

    public SecurityProblemWriter(ObjectMapper objectMapper, Clock clock) {
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public void writeUnauthorized(
            HttpServletRequest request, HttpServletResponse response, ErrorCode errorCode)
            throws IOException {
        write(
                request,
                response,
                HttpStatus.UNAUTHORIZED,
                errorCode,
                titleFor(errorCode),
                detailFor(errorCode));
    }

    public void writeForbidden(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        write(
                request,
                response,
                HttpStatus.FORBIDDEN,
                ErrorCode.ACCESS_DENIED,
                titleFor(ErrorCode.ACCESS_DENIED),
                detailFor(ErrorCode.ACCESS_DENIED));
    }

    public void write(
            HttpServletRequest request,
            HttpServletResponse response,
            HttpStatus status,
            ErrorCode errorCode,
            String title,
            String detail)
            throws IOException {
        if (response.isCommitted()) {
            return;
        }

        ObjectNode problem = objectMapper.createObjectNode();
        problem.put("type", problemType(errorCode));
        problem.put("title", title);
        problem.put("status", status.value());
        problem.put("detail", detail);
        problem.put("instance", request.getRequestURI());
        problem.put(ErrorCode.CODE_EXTENSION, errorCode.value());
        problem.put(ErrorCode.TRACE_ID_EXTENSION, resolveTraceId(request));
        problem.put(ErrorCode.TIMESTAMP_EXTENSION, Instant.now(clock).toString());

        response.setStatus(status.value());
        response.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), problem);
    }

    String resolveTraceId(HttpServletRequest request) {
        Object requestTraceId = request.getAttribute(CorrelationIdFilter.TRACE_ID_ATTRIBUTE);
        if (requestTraceId instanceof String value && StringUtils.hasText(value)) {
            return value;
        }
        String mappedTraceId = MDC.get(CorrelationIdFilter.MDC_TRACE_ID);
        return StringUtils.hasText(mappedTraceId) ? mappedTraceId : UUID.randomUUID().toString();
    }

    static String titleFor(ErrorCode errorCode) {
        return switch (errorCode) {
            case AUTHENTICATION_REQUIRED -> "Authentication Required";
            case AUTHENTICATION_FAILED -> "Authentication Failed";
            case MALFORMED_TOKEN -> "Malformed Token";
            case EXPIRED_TOKEN -> "Expired Token";
            case ACCESS_DENIED -> "Access Denied";
            default -> throw new IllegalArgumentException(
                    "Unsupported security error code: " + errorCode.value());
        };
    }

    static String detailFor(ErrorCode errorCode) {
        return switch (errorCode) {
            case AUTHENTICATION_REQUIRED -> "Authentication is required";
            case AUTHENTICATION_FAILED -> "Authentication failed";
            case MALFORMED_TOKEN -> "The access token is malformed";
            case EXPIRED_TOKEN -> "The access token has expired";
            case ACCESS_DENIED -> "Access is denied";
            default -> throw new IllegalArgumentException(
                    "Unsupported security error code: " + errorCode.value());
        };
    }

    private static String problemType(ErrorCode errorCode) {
        String suffix = errorCode.value().toLowerCase(Locale.ROOT).replace('_', '-');
        return PROBLEM_TYPE_PREFIX + suffix;
    }
}
