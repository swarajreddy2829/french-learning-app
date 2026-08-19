package com.example.frenchlearning.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * Returns the contract-defined 403 RFC 9457 response without leaking authorities or token claims.
 */
@Component
public final class BearerAccessDeniedHandler implements AccessDeniedHandler {

    private final SecurityProblemWriter problemWriter;

    public BearerAccessDeniedHandler(SecurityProblemWriter problemWriter) {
        this.problemWriter = problemWriter;
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException)
            throws IOException {
        problemWriter.writeForbidden(request, response);
    }
}
