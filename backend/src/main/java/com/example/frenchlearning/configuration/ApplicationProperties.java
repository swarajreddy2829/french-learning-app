package com.example.frenchlearning.configuration;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.net.URI;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import org.springframework.util.StringUtils;

@Validated
@ConfigurationProperties(prefix = "app")
public record ApplicationProperties(
        @NotNull @Valid Database database,
        @NotNull @Valid Jwt jwt,
        @NotNull @Valid Pagination pagination,
        @NotNull @Valid Bootstrap bootstrap) {

    public record Database(
            @NotBlank
                    @Pattern(
                            regexp = "^jdbc:postgresql://\\S+$",
                            message = "must be a PostgreSQL JDBC URL")
                    String url,
            @NotBlank String username,
            @NotBlank String password,
            @Positive int maximumPoolSize,
            @Min(0) int minimumIdle,
            @Min(250) long connectionTimeoutMs,
            @Min(250) long validationTimeoutMs) {

        @AssertTrue(message = "minimum idle must not exceed maximum pool size")
        public boolean isPoolSizingValid() {
            return minimumIdle <= maximumPoolSize;
        }

        @AssertTrue(message = "validation timeout must not exceed connection timeout")
        public boolean isTimeoutOrderingValid() {
            return validationTimeoutMs <= connectionTimeoutMs;
        }

        @Override
        public String toString() {
            return "Database[url=%s, username=%s, password=<redacted>, maximumPoolSize=%d, "
                    + "minimumIdle=%d, connectionTimeoutMs=%d, validationTimeoutMs=%d]"
                            .formatted(
                                    url,
                                    username,
                                    maximumPoolSize,
                                    minimumIdle,
                                    connectionTimeoutMs,
                                    validationTimeoutMs);
        }
    }

    public record Jwt(
            @NotNull URI issuer,
            @NotBlank String audience,
            @NotBlank String privateKeyPath,
            @NotBlank String publicKeyPath,
            @NotNull Duration accessTokenTtl) {

        private static final Set<String> SUPPORTED_ISSUER_SCHEMES = Set.of("http", "https");

        @AssertTrue(message = "issuer must be an absolute HTTP or HTTPS URI")
        public boolean isIssuerValid() {
            if (issuer == null || issuer.getScheme() == null) {
                return issuer == null;
            }
            return issuer.isAbsolute()
                    && SUPPORTED_ISSUER_SCHEMES.contains(
                            issuer.getScheme().toLowerCase(Locale.ROOT));
        }

        @AssertTrue(message = "access token TTL must be positive")
        public boolean isAccessTokenTtlValid() {
            return accessTokenTtl == null || (!accessTokenTtl.isZero() && !accessTokenTtl.isNegative());
        }
    }

    public record Pagination(
            @Min(1) @Max(100) int defaultPageSize,
            @Min(1) @Max(100) int maximumPageSize) {

        @AssertTrue(message = "default page size must not exceed maximum page size")
        public boolean isDefaultWithinMaximum() {
            return defaultPageSize <= maximumPageSize;
        }
    }

    public record Bootstrap(@NotNull @Valid Admin admin) {}

    public record Admin(boolean enabled, @Email String email, String password) {

        @AssertTrue(message = "email and password are required when admin bootstrap is enabled")
        public boolean isCompleteWhenEnabled() {
            return !enabled || (StringUtils.hasText(email) && StringUtils.hasText(password));
        }

        @Override
        public String toString() {
            return "Admin[enabled=%s, email=%s, password=<redacted>]".formatted(enabled, email);
        }
    }
}
