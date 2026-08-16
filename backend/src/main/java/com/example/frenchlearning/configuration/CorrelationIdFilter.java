package com.example.frenchlearning.configuration;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;

public final class CorrelationIdFilter extends OncePerRequestFilter implements Ordered {

    public static final String TRACE_ID_ATTRIBUTE = "traceId";
    public static final String MDC_TRACE_ID = "traceId";

    private static final String REQUEST_COMPLETED_MESSAGE =
            "request completed method={} path={} status={} durationMs={} traceId={}";

    private final Logger requestLogger;

    public CorrelationIdFilter() {
        this(LoggerFactory.getLogger(LoggingConfiguration.REQUEST_LOGGER_NAME));
    }

    CorrelationIdFilter(Logger requestLogger) {
        this.requestLogger = requestLogger;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {
        String traceId = UUID.randomUUID().toString();
        long startedAt = System.nanoTime();
        request.setAttribute(TRACE_ID_ATTRIBUTE, traceId);
        MDC.put(MDC_TRACE_ID, traceId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            long durationMs =
                    TimeUnit.NANOSECONDS.toMillis(Math.max(0, System.nanoTime() - startedAt));
            try {
                requestLogger.info(
                        REQUEST_COMPLETED_MESSAGE,
                        request.getMethod(),
                        request.getRequestURI(),
                        response.getStatus(),
                        durationMs,
                        traceId);
            } finally {
                MDC.remove(MDC_TRACE_ID);
                request.removeAttribute(TRACE_ID_ATTRIBUTE);
            }
        }
    }
}
