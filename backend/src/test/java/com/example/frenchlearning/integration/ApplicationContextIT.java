package com.example.frenchlearning.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles(profiles = "prod", inheritProfiles = true)
class ApplicationContextIT extends PostgresIntegrationTest {

    @Autowired
    private ConfigurableApplicationContext applicationContext;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private Environment environment;

    @Test
    void applicationContextStartsWithContainerDatasource() throws SQLException {
        assertThat(applicationContext.isActive()).isTrue();
        assertThat(POSTGRES.isRunning()).isTrue();
        assertThat(environment.getProperty("spring.datasource.url"))
                .isEqualTo(POSTGRES.getJdbcUrl());
        assertThat(environment.getProperty("spring.datasource.username"))
                .isEqualTo(POSTGRES.getUsername());
        assertThat(environment.getProperty("spring.datasource.password"))
                .isEqualTo(POSTGRES.getPassword());

        try (Connection connection = dataSource.getConnection()) {
            assertThat(connection.isValid(2)).isTrue();
            assertThat(connection.getMetaData().getDatabaseProductName())
                    .isEqualTo("PostgreSQL");
        }
    }

    @Test
    void productionProfileLoadsExpectedBaselineConfiguration() {
        assertThat(Arrays.asList(environment.getActiveProfiles()))
                .contains("test", "prod");
        assertThat(environment.getProperty("spring.application.name"))
                .isEqualTo("french-learning-backend");
        assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto"))
                .isEqualTo("validate");
        assertThat(environment.getProperty("server.error.include-stacktrace"))
                .isEqualTo("never");
        assertThat(environment.getProperty("app.bootstrap.admin.enabled", Boolean.class))
                .isFalse();
    }
}
