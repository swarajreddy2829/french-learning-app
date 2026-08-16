package com.example.frenchlearning.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.frenchlearning.exception.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

class CorrelationIdFilterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ch.qos.logback.classic.Logger requestLogger =
            (ch.qos.logback.classic.Logger)
                    LoggerFactory.getLogger(LoggingConfiguration.REQUEST_LOGGER_NAME);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    private CorrelationIdFilter filter;

    @BeforeEach
    void setUp() {
        requestLogger.setLevel(Level.INFO);
        requestLogger.addAppender(appender);
        appender.start();
        filter = new CorrelationIdFilter();
    }

    @AfterEach
    void tearDown() {
        appender.stop();
        requestLogger.detachAppender(appender);
        MDC.clear();
    }

    @Test
    void generatesDistinctIdsAndClearsRequestContextBetweenRequests() throws Exception {
        List<String> observedTraceIds = new ArrayList<>();

        for (int requestNumber = 0; requestNumber < 2; requestNumber++) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/lessons");
            MockHttpServletResponse response = new MockHttpServletResponse();
            FilterChain chain = (servletRequest, servletResponse) -> {
                String requestTraceId = (String) servletRequest.getAttribute(
                        CorrelationIdFilter.TRACE_ID_ATTRIBUTE);
                observedTraceIds.add(requestTraceId);
                assertThat(MDC.get(CorrelationIdFilter.MDC_TRACE_ID)).isEqualTo(requestTraceId);
            };

            filter.doFilter(request, response, chain);

            assertThat(request.getAttribute(CorrelationIdFilter.TRACE_ID_ATTRIBUTE)).isNull();
            assertThat(MDC.get(CorrelationIdFilter.MDC_TRACE_ID)).isNull();
        }

        assertThat(observedTraceIds)
                .hasSize(2)
                .doesNotHaveDuplicates()
                .allSatisfy(value -> assertThatCodeIsUuid(value));
    }

    @Test
    void ignoresUnspecifiedIncomingIdsAndLogsOnlySafeRequestMetadata() throws Exception {
        MockHttpServletRequest request =
                new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.setQueryString("password=query-secret");
        request.addHeader("X-Correlation-ID", "attacker-controlled-id");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer secret-token");
        request.addHeader(HttpHeaders.COOKIE, "session=secret-cookie");
        request.setContent("{\"password\":\"body-secret\"}".getBytes());
        MockHttpServletResponse response = new MockHttpServletResponse();
        List<String> observedTraceIds = new ArrayList<>();

        filter.doFilter(
                request,
                response,
                (servletRequest, servletResponse) -> {
                    observedTraceIds.add((String) servletRequest.getAttribute(
                            CorrelationIdFilter.TRACE_ID_ATTRIBUTE));
                    ((MockHttpServletResponse) servletResponse).setStatus(204);
                });

        String traceId = observedTraceIds.getFirst();
        String logMessage = appender.list.getLast().getFormattedMessage();

        assertThatCodeIsUuid(traceId);
        assertThat(traceId).isNotEqualTo("attacker-controlled-id");
        assertThat(response.getHeader("X-Correlation-ID")).isNull();
        assertThat(logMessage)
                .contains(
                        "method=POST",
                        "path=/api/v1/auth/login",
                        "status=204",
                        "durationMs=",
                        "traceId=" + traceId)
                .doesNotContain(
                        "attacker-controlled-id",
                        "query-secret",
                        "secret-token",
                        "secret-cookie",
                        "body-secret",
                        "Authorization",
                        "Cookie",
                        "password");
    }

    @Test
    void usesTheRequestTraceIdInProblemResponsesAndCompletionLogs() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new FailureController())
                .setControllerAdvice(new GlobalExceptionHandler(Clock.systemUTC()))
                .addFilters(filter)
                .build();

        MvcResult result = mockMvc.perform(get("/failure")
                        .header("X-Correlation-ID", "untrusted-id"))
                .andExpect(status().isInternalServerError())
                .andReturn();

        JsonNode problem = objectMapper.readTree(result.getResponse().getContentAsString());
        String traceId = problem.path("traceId").asText();
        String logMessage = appender.list.getLast().getFormattedMessage();

        assertThatCodeIsUuid(traceId);
        assertThat(traceId).isNotEqualTo("untrusted-id");
        assertThat(logMessage).contains("status=500", "traceId=" + traceId);
        assertThat(MDC.get(CorrelationIdFilter.MDC_TRACE_ID)).isNull();
    }

    private void assertThatCodeIsUuid(String value) {
        assertThat(value).isNotBlank();
        assertThat(UUID.fromString(value).toString()).isEqualTo(value);
    }

    @RestController
    static class FailureController {

        @GetMapping("/failure")
        void fail() {
            throw new IllegalStateException("password=internal-secret");
        }
    }
}
