package com.example.frenchlearning.security;

import com.example.frenchlearning.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.text.ParseException;
import java.util.Locale;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Returns RFC 9457 bearer authentication failures without leaking token or decoder details.
 */
@Component
public final class BearerAuthenticationEntryPoint implements AuthenticationEntryPoint {

    static final String BEARER_CHALLENGE = "Bearer";
    static final String INVALID_TOKEN_CHALLENGE = "Bearer error=\"invalid_token\"";

    private final SecurityProblemWriter problemWriter;

    public BearerAuthenticationEntryPoint(SecurityProblemWriter problemWriter) {
        this.problemWriter = problemWriter;
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException)
            throws IOException {
        ErrorCode errorCode = resolveErrorCode(request, authException);
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, challengeFor(errorCode));
        problemWriter.writeUnauthorized(request, response, errorCode);
    }

    static String challengeFor(ErrorCode errorCode) {
        return errorCode == ErrorCode.AUTHENTICATION_REQUIRED
                ? BEARER_CHALLENGE
                : INVALID_TOKEN_CHALLENGE;
    }

    static ErrorCode resolveErrorCode(
            HttpServletRequest request, AuthenticationException authException) {
        if (isExpired(authException)) {
            return ErrorCode.EXPIRED_TOKEN;
        }
        if (isMalformedToken(authException) || isMalformedAuthorization(authException)) {
            return ErrorCode.MALFORMED_TOKEN;
        }
        if (!hasBearerCredentials(request)) {
            return ErrorCode.AUTHENTICATION_REQUIRED;
        }
        return ErrorCode.AUTHENTICATION_FAILED;
    }

    static boolean hasBearerCredentials(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(header) || header.length() < 7) {
            return false;
        }
        if (!header.regionMatches(true, 0, "Bearer", 0, 6)) {
            return false;
        }
        if (header.charAt(6) != ' ') {
            return false;
        }
        return StringUtils.hasText(header.substring(7));
    }

    private static boolean isMalformedAuthorization(AuthenticationException authException) {
        if (!(authException instanceof OAuth2AuthenticationException oauth2Exception)) {
            return false;
        }
        OAuth2Error error = oauth2Exception.getError();
        return error != null && "invalid_request".equals(error.getErrorCode());
    }

    private static boolean isExpired(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof JwtValidationException validationException
                    && hasExpiredError(validationException)) {
                return true;
            }
            if (containsJwtExpired(current.getMessage())) {
                return true;
            }
            if (current instanceof OAuth2AuthenticationException oauth2Exception
                    && containsJwtExpired(descriptionOf(oauth2Exception.getError()))) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static boolean hasExpiredError(JwtValidationException validationException) {
        return validationException.getErrors().stream()
                .anyMatch(error -> containsJwtExpired(error.getDescription()));
    }

    private static boolean containsJwtExpired(String value) {
        return value != null && value.toLowerCase(Locale.ROOT).contains("jwt expired");
    }

    private static boolean isMalformedToken(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof ParseException) {
                return true;
            }
            String className = current.getClass().getName();
            if (className.contains("Malformed") || className.endsWith("ParseException")) {
                return true;
            }
            if (!(current instanceof JwtValidationException)
                    && isMalformedMessage(current.getMessage())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static boolean isMalformedMessage(String message) {
        if (message == null) {
            return false;
        }
        String normalized = message.toLowerCase(Locale.ROOT);
        return normalized.contains("malformed")
                || normalized.contains("invalid jwt serialization")
                || normalized.contains("missing dot delimiter")
                || normalized.contains("jwt strings must contain");
    }

    private static String descriptionOf(OAuth2Error error) {
        return error == null ? null : error.getDescription();
    }
}
