package com.example.frenchlearning.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class ApplicationPropertiesTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(PropertiesConfiguration.class);

    @Test
    void bindsValidConfiguration() {
        validContext().run(context -> {
            assertThat(context).hasNotFailed();
            ApplicationProperties properties = context.getBean(ApplicationProperties.class);

            assertThat(properties.database().url())
                    .isEqualTo("jdbc:postgresql://localhost:5432/french_learning");
            assertThat(properties.database().maximumPoolSize()).isEqualTo(20);
            assertThat(properties.jwt().issuer())
                    .isEqualTo(URI.create("https://issuer.example.test"));
            assertThat(properties.jwt().accessTokenTtl()).isEqualTo(Duration.ofMinutes(15));
            assertThat(properties.pagination().defaultPageSize()).isEqualTo(20);
            assertThat(properties.pagination().maximumPageSize()).isEqualTo(100);
            assertThat(properties.bootstrap().admin().enabled()).isFalse();
        });
    }

    @Test
    void failsWhenRequiredConfigurationIsMissing() {
        contextRunner
                .withPropertyValues(
                        "app.database.username=french_app",
                        "app.database.password=test-password",
                        "app.database.maximum-pool-size=20",
                        "app.database.minimum-idle=2",
                        "app.database.connection-timeout-ms=30000",
                        "app.database.validation-timeout-ms=5000",
                        "app.jwt.issuer=https://issuer.example.test",
                        "app.jwt.audience=french-learning-api",
                        "app.jwt.private-key-path=/run/secrets/jwt-private.pem",
                        "app.jwt.public-key-path=/run/secrets/jwt-public.pem",
                        "app.jwt.access-token-ttl=15m",
                        "app.pagination.default-page-size=20",
                        "app.pagination.maximum-page-size=100",
                        "app.bootstrap.admin.enabled=false")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(BindValidationException.class)
                            .hasMessageContaining("app");
                });
    }

    @Test
    void failsWhenConfigurationBoundsOrDependenciesAreInvalid() {
        validContext()
                .withPropertyValues(
                        "app.database.minimum-idle=21",
                        "app.jwt.access-token-ttl=0s",
                        "app.pagination.default-page-size=101",
                        "app.bootstrap.admin.enabled=true",
                        "app.bootstrap.admin.email=",
                        "app.bootstrap.admin.password=")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(BindValidationException.class);
                });
    }

    private ApplicationContextRunner validContext() {
        return contextRunner.withPropertyValues(
                "app.database.url=jdbc:postgresql://localhost:5432/french_learning",
                "app.database.username=french_app",
                "app.database.password=test-password",
                "app.database.maximum-pool-size=20",
                "app.database.minimum-idle=2",
                "app.database.connection-timeout-ms=30000",
                "app.database.validation-timeout-ms=5000",
                "app.jwt.issuer=https://issuer.example.test",
                "app.jwt.audience=french-learning-api",
                "app.jwt.private-key-path=/run/secrets/jwt-private.pem",
                "app.jwt.public-key-path=/run/secrets/jwt-public.pem",
                "app.jwt.access-token-ttl=15m",
                "app.pagination.default-page-size=20",
                "app.pagination.maximum-page-size=100",
                "app.bootstrap.admin.enabled=false");
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(ApplicationProperties.class)
    static class PropertiesConfiguration {}
}
