package com.example.frenchlearning.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.frenchlearning.integration.PostgresIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@AutoConfigureMockMvc
class AuthControllerIT extends PostgresIntegrationTest {

    private static final String VALID_PASSWORD = "Learner-local-123!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void registersANewLearnerUsingTheDocumentedResponseEnvelope() throws Exception {
        mockMvc.perform(postJson(
                        "/api/v1/auth/register",
                        Map.of("email", "learner@example.test", "password", VALID_PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.data.id").isNotEmpty())
                .andExpect(jsonPath("$.data.email").value("learner@example.test"))
                .andExpect(jsonPath("$.data.roles", contains("USER")))
                .andExpect(jsonPath("$.data.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.data.password").doesNotExist())
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.meta").doesNotExist());
    }

    @Test
    void logsInARegisteredLearnerUsingTheDocumentedTokenEnvelope() throws Exception {
        String email = "login-success@example.test";
        register(email, VALID_PASSWORD);

        mockMvc.perform(postJson(
                        "/api/v1/auth/login",
                        Map.of("email", email, "password", VALID_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.expiresIn").isNumber())
                .andExpect(jsonPath("$.data.expiresIn").value(
                        org.hamcrest.Matchers.greaterThan(0)))
                .andExpect(jsonPath("$.meta").doesNotExist());
    }

    @Test
    void reportsAllSafelyDisclosableRegistrationValidationFailures() throws Exception {
        mockMvc.perform(postJson(
                        "/api/v1/auth/register",
                        Map.of("email", "not-an-email", "password", "short")))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath(
                        "$.fieldErrors[*].field", containsInAnyOrder("email", "password")))
                .andExpect(jsonPath(
                                "$.fieldErrors[?(@.field == 'password')].rejectedValue")
                        .doesNotExist());
    }

    @Test
    void rejectsDuplicateRegistrationUsingTheDocumentedConflictProblem() throws Exception {
        String email = "duplicate@example.test";
        register(email, VALID_PASSWORD);

        mockMvc.perform(postJson(
                        "/api/v1/auth/register",
                        Map.of("email", " DUPLICATE@EXAMPLE.TEST ", "password", VALID_PASSWORD)))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"))
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(content().string(
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(
                                VALID_PASSWORD))));
    }

    @Test
    void returnsTheSameGenericCredentialErrorForWrongPasswordAndUnknownEmail() throws Exception {
        String registeredEmail = "login-failure@example.test";
        register(registeredEmail, VALID_PASSWORD);

        MvcResult wrongPassword = mockMvc.perform(postJson(
                        "/api/v1/auth/login",
                        Map.of("email", registeredEmail, "password", "Wrong-password-123!")))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andReturn();
        MvcResult unknownEmail = mockMvc.perform(postJson(
                        "/api/v1/auth/login",
                        Map.of("email", "unknown@example.test", "password", VALID_PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andReturn();

        JsonNode wrongPasswordProblem =
                objectMapper.readTree(wrongPassword.getResponse().getContentAsByteArray());
        JsonNode unknownEmailProblem =
                objectMapper.readTree(unknownEmail.getResponse().getContentAsByteArray());

        assertThat(wrongPasswordProblem.path("code").asText())
                .isEqualTo("AUTHENTICATION_FAILED")
                .isEqualTo(unknownEmailProblem.path("code").asText());
        assertThat(wrongPasswordProblem.path("title").asText())
                .isEqualTo(unknownEmailProblem.path("title").asText());
        assertThat(wrongPasswordProblem.path("detail").asText())
                .isEqualTo(unknownEmailProblem.path("detail").asText());
        assertThat(wrongPassword.getResponse().getContentAsString())
                .doesNotContain(registeredEmail, "Wrong-password-123!");
        assertThat(unknownEmail.getResponse().getContentAsString())
                .doesNotContain("unknown@example.test", VALID_PASSWORD);
    }

    private void register(String email, String password) throws Exception {
        mockMvc.perform(postJson(
                        "/api/v1/auth/register", Map.of("email", email, "password", password)))
                .andExpect(status().isCreated());
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder postJson(
            String path, Object body) throws Exception {
        return post(path)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON, MediaType.APPLICATION_PROBLEM_JSON)
                .content(objectMapper.writeValueAsBytes(body));
    }
}
