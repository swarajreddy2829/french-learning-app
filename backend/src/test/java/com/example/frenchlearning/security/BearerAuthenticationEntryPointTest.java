package com.example.frenchlearning.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.frenchlearning.configuration.CorrelationIdFilter;
import com.example.frenchlearning.exception.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.text.ParseException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.server.resource.BearerTokenError;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

class BearerAuthenticationEntryPointTest {

    private static final Instant FIXED_TIME = Instant.parse("2026-08-19T06:30:00Z");
    private static final String TRACE_ID = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee";
    private static final String MALFORMED_TOKEN = "not-a-valid-jwt";
    private static final String EXPIRED_DECODER_MESSAGE =
            "Jwt expired at 2026-08-19T06:26:00Z";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private BearerAuthenticationEntryPoint entryPoint;

    @BeforeEach
    void setUp() {
        SecurityProblemWriter writer =
                new SecurityProblemWriter(objectMapper, Clock.fixed(FIXED_TIME, ZoneOffset.UTC));
        entryPoint = new BearerAuthenticationEntryPoint(writer);
    }

    @Test
    void mapsMissingBearerCredentialsToAuthenticationRequired() throws Exception {
        MockHttpServletRequest request = request("/test/security/user");
        MockHttpServletResponse response = new MockHttpServletResponse();

        entryPoint.commence(
                request,
                response,
                new InsufficientAuthenticationException(
                        "Full authentication is required to access this resource"));

        JsonNode problem = readBody(response);
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getHeader(HttpHeaders.WWW_AUTHENTICATE))
                .isEqualTo(BearerAuthenticationEntryPoint.BEARER_CHALLENGE)
                .startsWith("Bearer");
        assertThat(response.getContentType())
                .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        assertThat(problem.path("code").asText()).isEqualTo("AUTHENTICATION_REQUIRED");
        assertThat(problem.path("status").asInt()).isEqualTo(401);
        assertThat(problem.path("traceId").asText()).isEqualTo(TRACE_ID);
        assertThat(problem.path("timestamp").asText()).isEqualTo(FIXED_TIME.toString());
        assertThat(response.getContentAsString())
                .doesNotContain("Full authentication is required to access this resource");
    }

    @Test
    void mapsMalformedTokensWithoutExposingTheTokenOrDecoderMessage() throws Exception {
        ParseException parseException =
                new ParseException("Invalid JWT serialization: Missing dot delimiter(s)", 0);
        BadJwtException badJwt = new BadJwtException(
                "An error occurred while attempting to decode the Jwt: "
                        + parseException.getMessage(),
                parseException);

        JsonNode problem = commenceWithBearer(
                MALFORMED_TOKEN,
                new InvalidBearerTokenException(badJwt.getMessage(), badJwt));

        assertThat(problem.path("code").asText()).isEqualTo("MALFORMED_TOKEN");
        assertThat(problem.path("detail").asText()).isEqualTo("The access token is malformed");
        assertThat(problem.path("traceId").asText()).isEqualTo(TRACE_ID);
        String body = problem.toString();
        assertThat(body)
                .doesNotContain(MALFORMED_TOKEN)
                .doesNotContain("Invalid JWT serialization")
                .doesNotContain("decode the Jwt");
    }

    @Test
    void mapsExpiredTokensWithoutCopyingTheDecoderTimestamp() throws Exception {
        JwtValidationException validationException = new JwtValidationException(
                "An error occurred while attempting to decode the Jwt: " + EXPIRED_DECODER_MESSAGE,
                List.of(new OAuth2Error("invalid_token", EXPIRED_DECODER_MESSAGE, null)));

        MockHttpServletResponse response = new MockHttpServletResponse();
        entryPoint.commence(
                requestWithBearer("expired.jwt.token"),
                response,
                new InvalidBearerTokenException(validationException.getMessage(), validationException));

        JsonNode problem = objectMapper.readTree(response.getContentAsByteArray());
        assertThat(response.getHeader(HttpHeaders.WWW_AUTHENTICATE))
                .isEqualTo(BearerAuthenticationEntryPoint.INVALID_TOKEN_CHALLENGE)
                .startsWith("Bearer")
                .doesNotContain(EXPIRED_DECODER_MESSAGE)
                .doesNotContain("expired.jwt.token");
        assertThat(problem.path("code").asText()).isEqualTo("EXPIRED_TOKEN");
        assertThat(problem.path("detail").asText()).isEqualTo("The access token has expired");
        assertThat(response.getContentAsString())
                .doesNotContain(EXPIRED_DECODER_MESSAGE)
                .doesNotContain("expired.jwt.token");
    }

    @Test
    void mapsInvalidIssuerAudienceSignatureAndAlgorithmToAuthenticationFailed() throws Exception {
        assertAuthenticationFailed(
                jwtValidation("This iss claim is not equal to the configured issuer"));
        assertAuthenticationFailed(
                jwtValidation("An error occurred while attempting to check the Audience"));
        assertAuthenticationFailed(
                new BadJwtException(
                        "An error occurred while attempting to decode the Jwt: "
                                + "Signed JWT rejected: Invalid signature"));
        assertAuthenticationFailed(
                new BadJwtException(
                        "An error occurred while attempting to decode the Jwt: "
                                + "Unsupported algorithm of HS256"));
    }

    @Test
    void mapsMalformedAuthorizationHeadersToMalformedToken() throws Exception {
        BearerTokenError bearerTokenError = new BearerTokenError(
                "invalid_request",
                HttpStatus.BAD_REQUEST,
                "Bearer token is malformed",
                "https://tools.ietf.org/html/rfc6750#section-3.1");
        MockHttpServletRequest request = request("/test/security/user");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer token extra");
        MockHttpServletResponse response = new MockHttpServletResponse();

        entryPoint.commence(
                request, response, new OAuth2AuthenticationException(bearerTokenError));

        JsonNode problem = objectMapper.readTree(response.getContentAsByteArray());
        assertThat(problem.path("code").asText()).isEqualTo("MALFORMED_TOKEN");
        assertThat(response.getContentAsString())
                .doesNotContain("Bearer token is malformed")
                .doesNotContain("token extra");
    }

    private void assertAuthenticationFailed(Exception cause) throws Exception {
        InvalidBearerTokenException exception =
                new InvalidBearerTokenException(cause.getMessage(), cause);
        MockHttpServletResponse response = new MockHttpServletResponse();
        entryPoint.commence(requestWithBearer("signed.jwt.value"), response, exception);

        JsonNode problem = objectMapper.readTree(response.getContentAsByteArray());
        String body = response.getContentAsString();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getHeader(HttpHeaders.WWW_AUTHENTICATE)).startsWith("Bearer");
        assertThat(problem.path("code").asText()).isEqualTo("AUTHENTICATION_FAILED");
        assertThat(problem.path("detail").asText()).isEqualTo("Authentication failed");
        assertThat(body)
                .doesNotContain("signed.jwt.value")
                .doesNotContain("configured issuer")
                .doesNotContain("Invalid signature")
                .doesNotContain("HS256")
                .doesNotContain("Audience");
        assertThat(response.getHeader(HttpHeaders.WWW_AUTHENTICATE))
                .doesNotContain("signed.jwt.value")
                .doesNotContain("HS256");
    }

    private JsonNode commenceWithBearer(String token, InvalidBearerTokenException exception)
            throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        entryPoint.commence(requestWithBearer(token), response, exception);
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getHeader(HttpHeaders.WWW_AUTHENTICATE)).startsWith("Bearer");
        return objectMapper.readTree(response.getContentAsByteArray());
    }

    private JwtValidationException jwtValidation(String description) {
        return new JwtValidationException(
                "An error occurred while attempting to decode the Jwt: " + description,
                List.of(new OAuth2Error("invalid_token", description, null)));
    }

    private MockHttpServletRequest requestWithBearer(String token) {
        MockHttpServletRequest request = request("/test/security/user");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        return request;
    }

    private MockHttpServletRequest request(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.setAttribute(CorrelationIdFilter.TRACE_ID_ATTRIBUTE, TRACE_ID);
        return request;
    }

    private JsonNode readBody(MockHttpServletResponse response) throws Exception {
        return objectMapper.readTree(response.getContentAsByteArray());
    }
}
