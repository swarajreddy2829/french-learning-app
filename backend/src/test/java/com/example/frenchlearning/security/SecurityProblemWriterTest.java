package com.example.frenchlearning.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.frenchlearning.configuration.CorrelationIdFilter;
import com.example.frenchlearning.exception.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class SecurityProblemWriterTest {

    private static final Instant FIXED_TIME = Instant.parse("2026-08-19T06:30:00Z");
    private static final String REQUEST_TRACE_ID = "11111111-1111-1111-1111-111111111111";
    private static final String MDC_TRACE_ID = "22222222-2222-2222-2222-222222222222";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private SecurityProblemWriter writer;

    @BeforeEach
    void setUp() {
        writer = new SecurityProblemWriter(objectMapper, Clock.fixed(FIXED_TIME, ZoneOffset.UTC));
    }

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void writesTheRfc9457SecurityProblemUsingT014Conventions() throws Exception {
        MockHttpServletRequest request = requestWithTrace("/api/v1/lessons", REQUEST_TRACE_ID);
        MockHttpServletResponse response = new MockHttpServletResponse();

        writer.writeUnauthorized(request, response, ErrorCode.AUTHENTICATION_REQUIRED);

        JsonNode problem = readBody(response);
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType())
                .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        assertThat(problem.path("type").asText())
                .isEqualTo("urn:problem-type:french-learning:authentication-required");
        assertThat(problem.path("title").asText()).isEqualTo("Authentication Required");
        assertThat(problem.path("status").asInt()).isEqualTo(401);
        assertThat(problem.path("detail").asText()).isEqualTo("Authentication is required");
        assertThat(problem.path("instance").asText()).isEqualTo("/api/v1/lessons");
        assertThat(problem.path("code").asText()).isEqualTo("AUTHENTICATION_REQUIRED");
        assertThat(problem.path("traceId").asText()).isEqualTo(REQUEST_TRACE_ID);
        assertThat(problem.path("timestamp").asText()).isEqualTo(FIXED_TIME.toString());
        assertThat(problem.has("fieldErrors")).isFalse();
    }

    @Test
    void prefersTheRequestTraceIdOverMdcAndDoesNotCopyCredentials() throws Exception {
        MDC.put(CorrelationIdFilter.MDC_TRACE_ID, MDC_TRACE_ID);
        MockHttpServletRequest request = requestWithTrace("/api/v1/auth/login", REQUEST_TRACE_ID);
        request.addHeader("Authorization", "Bearer secret-jwt-value");
        MockHttpServletResponse response = new MockHttpServletResponse();

        writer.writeUnauthorized(request, response, ErrorCode.AUTHENTICATION_FAILED);

        String body = response.getContentAsString();
        JsonNode problem = objectMapper.readTree(body);
        assertThat(problem.path("traceId").asText()).isEqualTo(REQUEST_TRACE_ID);
        assertThat(problem.path("code").asText()).isEqualTo("AUTHENTICATION_FAILED");
        assertThat(body)
                .doesNotContain("secret-jwt-value")
                .doesNotContain("Authorization")
                .doesNotContain("stackTrace");
    }

    @Test
    void fallsBackToTheMdcTraceIdWhenTheRequestAttributeIsAbsent() throws Exception {
        MDC.put(CorrelationIdFilter.MDC_TRACE_ID, MDC_TRACE_ID);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/admin");
        MockHttpServletResponse response = new MockHttpServletResponse();

        writer.writeForbidden(request, response);

        JsonNode problem = readBody(response);
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType())
                .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        assertThat(problem.path("type").asText())
                .isEqualTo("urn:problem-type:french-learning:access-denied");
        assertThat(problem.path("title").asText()).isEqualTo("Access Denied");
        assertThat(problem.path("status").asInt()).isEqualTo(403);
        assertThat(problem.path("detail").asText()).isEqualTo("Access is denied");
        assertThat(problem.path("instance").asText()).isEqualTo("/admin");
        assertThat(problem.path("code").asText()).isEqualTo("ACCESS_DENIED");
        assertThat(problem.path("traceId").asText()).isEqualTo(MDC_TRACE_ID);
        assertThat(problem.path("timestamp").asText()).isEqualTo(FIXED_TIME.toString());
    }

    @Test
    void generatesATraceIdWhenNeitherRequestNorMdcProvideOne() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/test/security/user");
        MockHttpServletResponse response = new MockHttpServletResponse();

        writer.write(
                request,
                response,
                HttpStatus.UNAUTHORIZED,
                ErrorCode.MALFORMED_TOKEN,
                SecurityProblemWriter.titleFor(ErrorCode.MALFORMED_TOKEN),
                SecurityProblemWriter.detailFor(ErrorCode.MALFORMED_TOKEN));

        JsonNode problem = readBody(response);
        String traceId = problem.path("traceId").asText();
        assertThat(UUID.fromString(traceId).toString()).isEqualTo(traceId);
        assertThat(problem.path("code").asText()).isEqualTo("MALFORMED_TOKEN");
        assertThat(problem.path("detail").asText()).isEqualTo("The access token is malformed");
    }

    private MockHttpServletRequest requestWithTrace(String path, String traceId) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.setAttribute(CorrelationIdFilter.TRACE_ID_ATTRIBUTE, traceId);
        return request;
    }

    private JsonNode readBody(MockHttpServletResponse response) throws Exception {
        return objectMapper.readTree(response.getContentAsByteArray());
    }
}
