package com.example.frenchlearning.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class GlobalExceptionHandlerTest {

    private static final Instant FIXED_TIME = Instant.parse("2026-08-16T14:30:00Z");

    private final ObjectMapper objectMapper = new ObjectMapper();

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(FIXED_TIME, ZoneOffset.UTC);
        mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler(clock))
                .build();
    }

    @Test
    void mapsResourceNotFoundAndConflictExceptions() throws Exception {
        mockMvc.perform(get("/test/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type")
                        .value("urn:problem-type:french-learning:resource-not-found"))
                .andExpect(jsonPath("$.title").value("Resource Not Found"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("Lesson was not found"))
                .andExpect(jsonPath("$.instance").value("/test/not-found"))
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        mockMvc.perform(get("/test/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"))
                .andExpect(jsonPath("$.detail").value("Email is already registered"));
    }

    @Test
    void mapsValidationErrorsToSafeFieldViolations() throws Exception {
        MvcResult result = mockMvc.perform(post("/test/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors", hasSize(2)))
                .andExpect(jsonPath("$.timestamp").value(FIXED_TIME.toString()))
                .andExpect(jsonPath("$.traceId", matchesPattern("^[0-9a-f-]{36}$")))
                .andReturn();

        JsonNode fieldErrors = objectMapper
                .readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("fieldErrors");
        JsonNode passwordViolation = StreamSupport.stream(fieldErrors.spliterator(), false)
                .filter(node -> "password".equals(node.path("field").asText()))
                .findFirst()
                .orElseThrow();

        assertThat(passwordViolation.path("code").asText()).isEqualTo("NotBlank");
        assertThat(passwordViolation.path("message").asText()).isEqualTo("must not be blank");
        assertThat(passwordViolation.has("rejectedValue")).isFalse();
    }

    @Test
    void mapsMalformedJsonWithoutExposingParserDetails() throws Exception {
        mockMvc.perform(post("/test/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.detail").value("Request body is malformed"))
                .andExpect(jsonPath("$.fieldErrors").doesNotExist());
    }

    @Test
    void mapsUnexpectedFailuresWithoutExposingInternalDetails() throws Exception {
        mockMvc.perform(get("/test/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Internal Server Error"))
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred"))
                .andExpect(jsonPath("$.detail").value(
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret"))))
                .andExpect(jsonPath("$.timestamp").value(FIXED_TIME.toString()))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @RestController
    @RequestMapping("/test")
    static class TestController {

        @GetMapping("/not-found")
        void notFound() {
            throw new ResourceNotFoundException("Lesson was not found");
        }

        @GetMapping("/conflict")
        void conflict() {
            throw new ConflictException(
                    ErrorCode.EMAIL_ALREADY_REGISTERED, "Email is already registered");
        }

        @PostMapping("/validation")
        void validate(@Valid @RequestBody TestRequest request) {}

        @GetMapping("/unexpected")
        void unexpected() {
            throw new IllegalStateException(
                    "password=secret jdbc:postgresql://internal/database");
        }
    }

    record TestRequest(@NotBlank String email, @NotBlank String password) {}
}
