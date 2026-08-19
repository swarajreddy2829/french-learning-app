package com.example.frenchlearning.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.frenchlearning.configuration.CorrelationIdFilter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;

class BearerAccessDeniedHandlerTest {

    private static final Instant FIXED_TIME = Instant.parse("2026-08-19T06:30:00Z");
    private static final String TRACE_ID = "99999999-8888-7777-6666-555555555555";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private BearerAccessDeniedHandler handler;

    @BeforeEach
    void setUp() {
        handler = new BearerAccessDeniedHandler(
                new SecurityProblemWriter(objectMapper, Clock.fixed(FIXED_TIME, ZoneOffset.UTC)));
    }

    @Test
    void writesTheContractForbiddenProblemWithoutLeakingAuthoritiesOrTokenClaims()
            throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/test/security/admin");
        request.setAttribute(CorrelationIdFilter.TRACE_ID_ATTRIBUTE, TRACE_ID);
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer user-jwt-with-roles-claim");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.handle(
                request,
                response,
                new AccessDeniedException(
                        "Access Denied, ROLE_ADMIN is required for authorities [ROLE_USER]"));

        JsonNode problem = objectMapper.readTree(response.getContentAsByteArray());
        String body = response.getContentAsString();
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType())
                .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        assertThat(response.getHeader(HttpHeaders.WWW_AUTHENTICATE)).isNull();
        assertThat(problem.path("type").asText())
                .isEqualTo("urn:problem-type:french-learning:access-denied");
        assertThat(problem.path("title").asText()).isEqualTo("Access Denied");
        assertThat(problem.path("status").asInt()).isEqualTo(403);
        assertThat(problem.path("detail").asText()).isEqualTo("Access is denied");
        assertThat(problem.path("instance").asText()).isEqualTo("/test/security/admin");
        assertThat(problem.path("code").asText()).isEqualTo("ACCESS_DENIED");
        assertThat(problem.path("traceId").asText()).isEqualTo(TRACE_ID);
        assertThat(problem.path("timestamp").asText()).isEqualTo(FIXED_TIME.toString());
        assertThat(body)
                .doesNotContain("ROLE_ADMIN")
                .doesNotContain("ROLE_USER")
                .doesNotContain("user-jwt-with-roles-claim")
                .doesNotContain("authorities")
                .doesNotContain("Access Denied, ROLE_ADMIN");
    }
}
