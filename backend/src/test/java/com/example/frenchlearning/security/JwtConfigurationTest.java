package com.example.frenchlearning.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.frenchlearning.configuration.ApplicationProperties;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

class JwtConfigurationTest {

    private static final Instant NOW = Instant.parse("2026-08-19T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final String ISSUER = "https://issuer.example.test";
    private static final String AUDIENCE = "french-learning-api-test";

    @TempDir
    Path tempDir;

    private JwtTestKeySupport.KeyFiles keys;
    private JwtDecoder decoder;
    private JwtAuthenticationConverter converter;
    private JwtSigningKeys signingKeys;

    @BeforeEach
    void setUp() throws Exception {
        keys = JwtTestKeySupport.writePkcs8Pair(tempDir);
        ApplicationProperties properties = properties(keys.privateKeyPath(), keys.publicKeyPath());
        JwtConfiguration configuration = new JwtConfiguration();
        signingKeys = configuration.jwtSigningKeys(properties, new DefaultResourceLoader());
        decoder = configuration.jwtDecoder(signingKeys, properties, CLOCK);
        converter = configuration.jwtAuthenticationConverter();
    }

    @Test
    void loadsRsaKeysWithoutExposingPrivateMaterial() throws Exception {
        assertThat(signingKeys.toString())
                .contains("kid=")
                .contains("RS256")
                .doesNotContain("BEGIN")
                .doesNotContain(Base64Snippet.privateKeySnippet(keys));
        assertThat(signingKeys.rsaKey().getAlgorithm()).isEqualTo(JWSAlgorithm.RS256);
        assertThat(signingKeys.rsaKey().toRSAPublicKey().getModulus()).isEqualTo(keys.publicKey().getModulus());
    }

    @Test
    void rejectsMissingAndMalformedKeyMaterialWithoutLeakingPem() throws Exception {
        Path missing = tempDir.resolve("missing.pem");
        Path malformed = tempDir.resolve("malformed.pem");
        Files.writeString(malformed, "not-a-pem");
        JwtConfiguration configuration = new JwtConfiguration();

        assertThatThrownBy(() -> configuration.jwtSigningKeys(
                        properties(missing, keys.publicKeyPath()), new DefaultResourceLoader()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not found")
                .hasMessageNotContaining("BEGIN");

        assertThatThrownBy(() -> configuration.jwtSigningKeys(
                        properties(malformed, keys.publicKeyPath()), new DefaultResourceLoader()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("malformed")
                .hasMessageNotContaining("not-a-pem");

        JwtTestKeySupport.KeyFiles other = JwtTestKeySupport.writePkcs8Pair(tempDir.resolve("other"));
        assertThatThrownBy(() -> configuration.jwtSigningKeys(
                        properties(keys.privateKeyPath(), other.publicKeyPath()),
                        new DefaultResourceLoader()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("does not match");
    }

    @Test
    void decoderAcceptsRs256TokensWithConfiguredIssuerAndAudience() throws Exception {
        Jwt jwt = decoder.decode(signedToken(keys, ISSUER, AUDIENCE, NOW, NOW.plusSeconds(300), List.of("USER")));

        assertThat(jwt.getHeaders().get("alg")).isEqualTo("RS256");
        assertThat(jwt.getIssuer()).hasToString(ISSUER);
        assertThat(jwt.getAudience()).containsExactly(AUDIENCE);
        assertThat(converter.convert(jwt).getAuthorities())
                .extracting(authority -> authority.getAuthority())
                .containsExactly("ROLE_USER");
    }

    @Test
    void convertsOnlyRolesPresentInTheToken() throws Exception {
        Jwt jwt = decoder.decode(signedToken(keys, ISSUER, AUDIENCE, NOW, NOW.plusSeconds(300), List.of("USER", "ADMIN")));

        assertThat(converter.convert(jwt).getAuthorities())
                .extracting(authority -> authority.getAuthority())
                .containsExactly("ROLE_USER", "ROLE_ADMIN");
        assertThat(converter.convert(jwt).getName()).isEqualTo(jwt.getSubject());
    }

    @Test
    void decoderRejectsIssuerAudienceExpiryAlgorithmAndSignatureFailures() throws Exception {
        assertValidationFailure(signedToken(keys, "https://other.example.test", AUDIENCE, NOW, NOW.plusSeconds(300), List.of("USER")));
        assertValidationFailure(signedToken(keys, ISSUER, "other-audience", NOW, NOW.plusSeconds(300), List.of("USER")));
        assertValidationFailure(signedToken(keys, ISSUER, AUDIENCE, NOW.minusSeconds(600), NOW.minusSeconds(300), List.of("USER")));

        JwtTestKeySupport.KeyFiles other = JwtTestKeySupport.writePkcs8Pair(tempDir.resolve("other"));
        assertThatThrownBy(() -> decoder.decode(
                        signedToken(other, ISSUER, AUDIENCE, NOW, NOW.plusSeconds(300), List.of("USER"))))
                .isInstanceOf(JwtException.class)
                .hasMessageNotContaining("BEGIN");

        assertThatThrownBy(() -> decoder.decode(hmacToken()))
                .isInstanceOf(BadJwtException.class)
                .hasMessageNotContaining("BEGIN");
    }

    private void assertValidationFailure(String token) {
        assertThatThrownBy(() -> decoder.decode(token))
                .isInstanceOf(JwtValidationException.class)
                .satisfies(exception -> {
                    JwtValidationException validationException = (JwtValidationException) exception;
                    assertThat(validationException.getErrors())
                            .extracting(OAuth2Error::getErrorCode)
                            .isNotEmpty();
                    assertThat(exception.toString()).doesNotContain("BEGIN PRIVATE KEY");
                });
    }

    private static String signedToken(
            JwtTestKeySupport.KeyFiles keyFiles,
            String issuer,
            String audience,
            Instant issuedAt,
            Instant expiresAt,
            List<String> roles)
            throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(issuer)
                .audience(audience)
                .subject(UUID.randomUUID().toString())
                .issueTime(Date.from(issuedAt))
                .notBeforeTime(Date.from(issuedAt))
                .expirationTime(Date.from(expiresAt))
                .jwtID(UUID.randomUUID().toString())
                .claim("roles", roles)
                .build();
        SignedJWT token = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
        token.sign(new RSASSASigner(keyFiles.privateKey()));
        return token.serialize();
    }

    private static String hmacToken() throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .audience(AUDIENCE)
                .subject(UUID.randomUUID().toString())
                .issueTime(Date.from(NOW))
                .expirationTime(Date.from(NOW.plusSeconds(300)))
                .claim("roles", List.of("USER"))
                .build();
        SignedJWT token = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        token.sign(new MACSigner(new byte[32]));
        return token.serialize();
    }

    private static ApplicationProperties properties(Path privateKeyPath, Path publicKeyPath) {
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
                        URI.create(ISSUER),
                        AUDIENCE,
                        privateKeyPath.toUri().toString(),
                        publicKeyPath.toUri().toString(),
                        Duration.ofMinutes(15)),
                new ApplicationProperties.Pagination(20, 100),
                new ApplicationProperties.Bootstrap(
                        new ApplicationProperties.Admin(false, "", "")));
    }

    private static final class Base64Snippet {
        private static String privateKeySnippet(JwtTestKeySupport.KeyFiles keyFiles) {
            String pem = JwtTestKeySupport.pem("PRIVATE KEY", keyFiles.privateKey().getEncoded());
            return pem.replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s+", "")
                    .substring(0, 24);
        }
    }
}
