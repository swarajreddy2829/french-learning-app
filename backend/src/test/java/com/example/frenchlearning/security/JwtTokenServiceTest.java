package com.example.frenchlearning.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.frenchlearning.configuration.ApplicationProperties;
import com.example.frenchlearning.user.domain.UserStatus;
import com.nimbusds.jwt.SignedJWT;
import java.net.URI;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;

class JwtTokenServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-19T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final UUID PUBLIC_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final String EMAIL = "Learner@Example.Test";
    private static final String NORMALIZED_EMAIL = "learner@example.test";
    private static final String PASSWORD_HASH = "{argon2}protected-value";

    @TempDir
    Path tempDir;

    private JwtDecoder decoder;
    private JwtTokenService tokenService;

    @BeforeEach
    void setUp() throws Exception {
        JwtTestKeySupport.KeyFiles keys = JwtTestKeySupport.writePkcs8Pair(tempDir);
        ApplicationProperties properties = properties(keys);
        JwtConfiguration configuration = new JwtConfiguration();
        JwtSigningKeys signingKeys = configuration.jwtSigningKeys(properties, new DefaultResourceLoader());
        JwtEncoder encoder = configuration.jwtEncoder(signingKeys);
        decoder = configuration.jwtDecoder(signingKeys, properties, CLOCK);
        tokenService = new JwtTokenService(encoder, properties, CLOCK);
    }

    @Test
    void issuesAnRs256AccessTokenWithRequiredClaimsAndFifteenMinuteLifetime() throws Exception {
        AuthenticatedUser user = new AuthenticatedUser(
                PUBLIC_ID,
                NORMALIZED_EMAIL,
                EMAIL,
                PASSWORD_HASH,
                List.of(
                        new SimpleGrantedAuthority("ROLE_USER"),
                        new SimpleGrantedAuthority("ROLE_ADMIN")),
                UserStatus.ACTIVE);

        String token = tokenService.issueAccessToken(user);
        Jwt jwt = decoder.decode(token);
        SignedJWT parsed = SignedJWT.parse(token);

        assertThat(parsed.getHeader().getAlgorithm().getName()).isEqualTo("RS256");
        assertThat(jwt.getSubject()).isEqualTo(PUBLIC_ID.toString());
        assertThat(jwt.getIssuer()).hasToString("https://issuer.example.test");
        assertThat(jwt.getAudience()).containsExactly("french-learning-api-test");
        assertThat(jwt.getIssuedAt()).isEqualTo(NOW);
        assertThat(jwt.getNotBefore()).isEqualTo(NOW);
        assertThat(jwt.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(15)));
        assertThat(jwt.getId()).isNotBlank();
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("USER", "ADMIN");
        assertThat(token).doesNotContain(EMAIL, NORMALIZED_EMAIL, PASSWORD_HASH);
        assertThat(jwt.getClaims())
                .doesNotContainKeys("email", "username", "password", "passwordHash", "normalizedEmail");
        assertThat(jwt.getTokenValue()).doesNotContain(PASSWORD_HASH);
    }

    private static ApplicationProperties properties(JwtTestKeySupport.KeyFiles keys) {
        return new ApplicationProperties(
                new ApplicationProperties.Database(
                        "jdbc:postgresql://localhost:5432/french_learning",
                        "french_app",
                        "test-password",
                        20,
                        2,
                        30000,
                        5000),
                new ApplicationProperties.Jwt(
                        URI.create("https://issuer.example.test"),
                        "french-learning-api-test",
                        keys.privateKeyPath().toUri().toString(),
                        keys.publicKeyPath().toUri().toString(),
                        Duration.ofMinutes(15)),
                new ApplicationProperties.Pagination(20, 100),
                new ApplicationProperties.Bootstrap(new ApplicationProperties.Admin(false, "", "")));
    }
}
