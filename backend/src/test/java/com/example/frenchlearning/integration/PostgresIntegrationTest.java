package com.example.frenchlearning.integration;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
public abstract class PostgresIntegrationTest {

    private static final DockerImageName POSTGRES_IMAGE =
            DockerImageName.parse("postgres:16-alpine");

    @Container
    protected static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(POSTGRES_IMAGE)
                    .withDatabaseName("french_learning_test")
                    .withUsername("french_test")
                    .withPassword("french_test");

    @DynamicPropertySource
    static void registerPostgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
