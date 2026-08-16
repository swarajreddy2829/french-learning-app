package com.example.frenchlearning.exception;

import com.example.frenchlearning.configuration.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String PROBLEM_TYPE_PREFIX = "urn:problem-type:french-learning:";
    private static final Set<String> SENSITIVE_FIELD_MARKERS =
            Set.of("password", "secret", "token", "key", "authorization", "credential");

    private final Clock clock;

    public GlobalExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ProblemDetail> handleApiException(
            ApiException exception, HttpServletRequest request) {
        return response(
                exception.getStatus(),
                exception.getErrorCode(),
                exception.getTitle(),
                exception.getMessage(),
                List.of(),
                request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception, HttpServletRequest request) {
        List<FieldViolation> fieldErrors = exception.getBindingResult().getFieldErrors().stream()
                .map(this::toFieldViolation)
                .toList();
        return response(
                HttpStatus.BAD_REQUEST,
                ErrorCode.VALIDATION_FAILED,
                "Validation Failed",
                "Request validation failed",
                fieldErrors,
                request);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<ProblemDetail> handleHandlerMethodValidation(
            HandlerMethodValidationException exception, HttpServletRequest request) {
        List<FieldViolation> fieldErrors =
                exception.getAllErrors().stream().map(this::toFieldViolation).toList();
        return response(
                HttpStatus.BAD_REQUEST,
                ErrorCode.VALIDATION_FAILED,
                "Validation Failed",
                "Request validation failed",
                fieldErrors,
                request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ProblemDetail> handleConstraintViolation(
            ConstraintViolationException exception, HttpServletRequest request) {
        List<FieldViolation> fieldErrors =
                exception.getConstraintViolations().stream().map(this::toFieldViolation).toList();
        return response(
                HttpStatus.BAD_REQUEST,
                ErrorCode.VALIDATION_FAILED,
                "Validation Failed",
                "Request validation failed",
                fieldErrors,
                request);
    }

    @ExceptionHandler({
        HttpMessageNotReadableException.class,
        MethodArgumentTypeMismatchException.class
    })
    ResponseEntity<ProblemDetail> handleMalformedRequest(
            Exception exception, HttpServletRequest request) {
        return response(
                HttpStatus.BAD_REQUEST,
                ErrorCode.MALFORMED_REQUEST,
                "Malformed Request",
                "Request body is malformed",
                List.of(),
                request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(
            Exception exception, HttpServletRequest request) {
        return response(
                HttpStatus.INTERNAL_SERVER_ERROR,
                ErrorCode.INTERNAL_ERROR,
                "Internal Server Error",
                "An unexpected error occurred",
                List.of(),
                request);
    }

    private ResponseEntity<ProblemDetail> response(
            HttpStatus status,
            ErrorCode errorCode,
            String title,
            String detail,
            List<FieldViolation> fieldErrors,
            HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(problemType(errorCode));
        problem.setTitle(title);
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty(ErrorCode.CODE_EXTENSION, errorCode.value());
        problem.setProperty(ErrorCode.TRACE_ID_EXTENSION, resolveTraceId(request));
        problem.setProperty(ErrorCode.TIMESTAMP_EXTENSION, Instant.now(clock).toString());
        if (!fieldErrors.isEmpty()) {
            problem.setProperty(ErrorCode.FIELD_ERRORS_EXTENSION, fieldErrors);
        }
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    private FieldViolation toFieldViolation(FieldError error) {
        String code = firstText(error.getCode(), "Invalid");
        String message = firstText(error.getDefaultMessage(), "Invalid value");
        return new FieldViolation(
                error.getField(),
                code,
                message,
                safeRejectedValue(error.getField(), error.getRejectedValue()));
    }

    private FieldViolation toFieldViolation(MessageSourceResolvable error) {
        String[] codes = error.getCodes();
        String code = codes == null || codes.length == 0 ? "Invalid" : codes[0];
        String message = firstText(error.getDefaultMessage(), "Invalid value");
        return FieldViolation.of("request", code, message);
    }

    private FieldViolation toFieldViolation(ConstraintViolation<?> violation) {
        String field = violation.getPropertyPath().toString();
        String code = violation.getConstraintDescriptor()
                .getAnnotation()
                .annotationType()
                .getSimpleName();
        return new FieldViolation(
                field,
                code,
                firstText(violation.getMessage(), "Invalid value"),
                safeRejectedValue(field, violation.getInvalidValue()));
    }

    private Object safeRejectedValue(String field, Object rejectedValue) {
        if (rejectedValue == null || isSensitiveField(field)) {
            return null;
        }
        if (rejectedValue instanceof CharSequence
                || rejectedValue instanceof Number
                || rejectedValue instanceof Boolean
                || rejectedValue instanceof Character
                || rejectedValue instanceof Enum<?>) {
            return rejectedValue;
        }
        return null;
    }

    private boolean isSensitiveField(String field) {
        String normalized = field.toLowerCase(Locale.ROOT);
        return SENSITIVE_FIELD_MARKERS.stream().anyMatch(normalized::contains);
    }

    private String resolveTraceId(HttpServletRequest request) {
        Object requestTraceId = request.getAttribute(CorrelationIdFilter.TRACE_ID_ATTRIBUTE);
        if (requestTraceId instanceof String value && StringUtils.hasText(value)) {
            return value;
        }
        String mappedTraceId = MDC.get(CorrelationIdFilter.MDC_TRACE_ID);
        return StringUtils.hasText(mappedTraceId) ? mappedTraceId : UUID.randomUUID().toString();
    }

    private URI problemType(ErrorCode errorCode) {
        String suffix = errorCode.value().toLowerCase(Locale.ROOT).replace('_', '-');
        return URI.create(PROBLEM_TYPE_PREFIX + suffix);
    }

    private String firstText(String candidate, String fallback) {
        return StringUtils.hasText(candidate) ? candidate : fallback;
    }
}
