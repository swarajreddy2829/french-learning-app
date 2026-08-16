package com.example.frenchlearning.security;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.frenchlearning.integration.PostgresIntegrationTest;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@AutoConfigureMockMvc
@Import(JwtSecurityIT.SecurityProbeController.class)
class JwtSecurityIT extends PostgresIntegrationTest {

    private static final String ISSUER = "https://issuer.example.test";
    private static final String AUDIENCE = "french-learning-api-test";
    private static final String KEY_ID = "jwt-security-it";
    private static final Instant NOW = Instant.now();
    private static final KeyMaterial KEYS = createKeyMaterial();
    private static final RSAKey RSA_JWK = new RSAKey.Builder(KEYS.publicKey())
            .privateKey(KEYS.privateKey())
            .keyID(KEY_ID)
            .build();

    @Autowired
    private MockMvc mockMvc;

    @DynamicPropertySource
    static void registerJwtProperties(DynamicPropertyRegistry registry) {
        registry.add("app.jwt.issuer", () -> ISSUER);
        registry.add("app.jwt.audience", () -> AUDIENCE);
        registry.add("app.jwt.private-key-path", () -> KEYS.privateKeyPath().toUri().toString());
        registry.add("app.jwt.public-key-path", () -> KEYS.publicKeyPath().toUri().toString());
    }

    @Test
    void acceptsOnlyTheConfiguredIssuerAndAudience() throws Exception {
        mockMvc.perform(get("/test/security/user").with(bearer(validToken("USER"))))
                .andExpect(status().isOk());

        expectAuthenticationFailure(token(claims -> claims.issuer("https://other.example.test")))
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"));
        expectAuthenticationFailure(token(claims -> claims.audience(List.of("other-audience"))))
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"));
    }

    @Test
    void rejectsExpiredTokensWithAStableProblemCode() throws Exception {
        String expiredToken = token(claims -> {
            claims.issuedAt(NOW.minusSeconds(600));
            claims.notBefore(NOW.minusSeconds(600));
            claims.expiresAt(NOW.minusSeconds(300));
        });

        expectAuthenticationFailure(expiredToken)
                .andExpect(jsonPath("$.code").value("EXPIRED_TOKEN"));
    }

    @Test
    void rejectsTokensSignedWithAnAlgorithmOutsideTheAllowlist() throws Exception {
        expectAuthenticationFailure(hmacToken())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"));
    }

    @Test
    void mapsJwtRolesToSpringSecurityAuthorities() throws Exception {
        mockMvc.perform(get("/test/security/user").with(bearer(validToken("USER"))))
                .andExpect(status().isOk())
                .andExpect(content().string("user"));

        mockMvc.perform(get("/test/security/admin").with(bearer(validToken("USER"))))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mockMvc.perform(get("/test/security/admin").with(bearer(validToken("ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(content().string("admin"));
    }

    @Test
    void rejectsMissingTokensAndPreservesTheBearerChallenge() throws Exception {
        mockMvc.perform(get("/test/security/user"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, startsWith("Bearer")))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    void rejectsMalformedTokensWithoutExposingTokenContents() throws Exception {
        String malformedToken = "not-a-valid-jwt";

        expectAuthenticationFailure(malformedToken)
                .andExpect(jsonPath("$.code").value("MALFORMED_TOKEN"))
                .andExpect(content().string(
                        org.hamcrest.Matchers.not(
                                org.hamcrest.Matchers.containsString(malformedToken))));
    }

    private ResultActions expectAuthenticationFailure(String token) throws Exception {
        return mockMvc.perform(get("/test/security/user").with(bearer(token)))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, startsWith("Bearer")))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    private String validToken(String role) {
        return token(claims -> claims.claim("roles", List.of(role)));
    }

    private String token(Consumer<JwtClaimsSet.Builder> customization) {
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .audience(List.of(AUDIENCE))
                .subject(UUID.randomUUID().toString())
                .issuedAt(NOW)
                .notBefore(NOW.minusSeconds(1))
                .expiresAt(NOW.plusSeconds(300))
                .id(UUID.randomUUID().toString())
                .claim("roles", List.of("USER"));
        customization.accept(claims);

        JWKSource<SecurityContext> jwkSource =
                (selector, context) -> selector.select(new JWKSet(RSA_JWK));
        JwtEncoder encoder = new NimbusJwtEncoder(jwkSource);
        JwsHeader header =
                JwsHeader.with(SignatureAlgorithm.RS256).keyId(KEY_ID).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
    }

    private String hmacToken() throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .audience(AUDIENCE)
                .subject(UUID.randomUUID().toString())
                .issueTime(Date.from(NOW))
                .notBeforeTime(Date.from(NOW.minusSeconds(1)))
                .expirationTime(Date.from(NOW.plusSeconds(300)))
                .jwtID(UUID.randomUUID().toString())
                .claim("roles", List.of("USER"))
                .build();
        SignedJWT token = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        token.sign(new MACSigner(new byte[32]));
        return token.serialize();
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor bearer(String token) {
        return request -> {
            request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
            return request;
        };
    }

    private static KeyMaterial createKeyMaterial() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair keyPair = generator.generateKeyPair();
            RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
            RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();

            Path directory = Files.createTempDirectory("jwt-security-it-");
            Path privateKeyPath = directory.resolve("private.pem");
            Path publicKeyPath = directory.resolve("public.pem");
            Files.writeString(
                    privateKeyPath,
                    pem("PRIVATE KEY", privateKey.getEncoded()),
                    StandardCharsets.US_ASCII);
            Files.writeString(
                    publicKeyPath,
                    pem("PUBLIC KEY", publicKey.getEncoded()),
                    StandardCharsets.US_ASCII);
            privateKeyPath.toFile().deleteOnExit();
            publicKeyPath.toFile().deleteOnExit();
            directory.toFile().deleteOnExit();
            return new KeyMaterial(publicKey, privateKey, publicKeyPath, privateKeyPath);
        } catch (Exception exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private static String pem(String type, byte[] encoded) {
        String body = Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(encoded);
        return "-----BEGIN " + type + "-----\n" + body + "\n-----END " + type + "-----\n";
    }

    private record KeyMaterial(
            RSAPublicKey publicKey,
            RSAPrivateKey privateKey,
            Path publicKeyPath,
            Path privateKeyPath) {}

    @RestController
    static class SecurityProbeController {

        @GetMapping("/test/security/user")
        @PreAuthorize("hasRole('USER')")
        String user() {
            return "user";
        }

        @GetMapping("/test/security/admin")
        @PreAuthorize("hasRole('ADMIN')")
        String admin() {
            return "admin";
        }
    }
}
