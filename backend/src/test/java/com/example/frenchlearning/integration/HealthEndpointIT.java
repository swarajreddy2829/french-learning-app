package com.example.frenchlearning.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthContributor;
import org.springframework.boot.actuate.health.HealthContributorRegistry;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc(addFilters = false)
class HealthEndpointIT extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private HealthContributorRegistry healthContributorRegistry;

    @Test
    void livenessReportsOnlyTheRunningApplicationState() throws Exception {
        mockMvc.perform(get("/actuator/health/liveness").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist())
                .andExpect(jsonPath("$.details").doesNotExist());
    }

    @Test
    void readinessReportsUpWhenPostgresIsAvailableWithoutExposingDetails() throws Exception {
        assertThat(POSTGRES.isRunning()).isTrue();
        assertThat(healthContributorRegistry.getContributor("db")).isNotNull();

        mockMvc.perform(get("/actuator/health/readiness").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist())
                .andExpect(jsonPath("$.details").doesNotExist());
    }

    @Test
    void readinessReportsUnavailableDependencyWhileLivenessRemainsUp() throws Exception {
        HealthContributor databaseContributor =
                healthContributorRegistry.unregisterContributor("db");
        assertThat(databaseContributor).isNotNull();

        HealthIndicator unavailableDatabase = () -> Health.down()
                .withDetail("password", "must-never-be-exposed")
                .withDetail("url", "jdbc:postgresql://internal/database")
                .build();
        healthContributorRegistry.registerContributor("db", unavailableDatabase);

        try {
            mockMvc.perform(get("/actuator/health/readiness").accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.status").value("DOWN"))
                    .andExpect(jsonPath("$.components").doesNotExist())
                    .andExpect(jsonPath("$.details").doesNotExist())
                    .andExpect(content().string(org.hamcrest.Matchers.not(
                            org.hamcrest.Matchers.containsString("must-never-be-exposed"))))
                    .andExpect(content().string(org.hamcrest.Matchers.not(
                            org.hamcrest.Matchers.containsString("jdbc:postgresql"))));

            mockMvc.perform(get("/actuator/health/liveness").accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("UP"));
        } finally {
            healthContributorRegistry.unregisterContributor("db");
            healthContributorRegistry.registerContributor("db", databaseContributor);
        }
    }
}
