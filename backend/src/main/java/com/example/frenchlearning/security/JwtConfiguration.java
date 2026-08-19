package com.example.frenchlearning.security;

import com.example.frenchlearning.configuration.ApplicationProperties;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import java.time.Clock;
import java.util.Collection;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.util.StringUtils;

/**
 * Loads external RSA key material and registers the Nimbus JWT encoder, decoder, and role converter.
 *
 * <p>New tokens are signed with RS256 only. The decoder allowlists that algorithm and validates
 * issuer, audience, and standard expiry/not-before claims. Private key material is never logged.
 */
@Configuration(proxyBeanMethods = false)
public class JwtConfiguration {

    static final SignatureAlgorithm SIGNING_ALGORITHM = SignatureAlgorithm.RS256;
    static final String ROLES_CLAIM = "roles";
    static final String AUTHORITY_PREFIX = "ROLE_";

    @Bean
    JwtSigningKeys jwtSigningKeys(ApplicationProperties properties, ResourceLoader resourceLoader) {
        ApplicationProperties.Jwt jwt = properties.jwt();
        return new JwtSigningKeys(JwtKeyLoader.load(jwt.privateKeyPath(), jwt.publicKeyPath(), resourceLoader));
    }

    @Bean
    JwtEncoder jwtEncoder(JwtSigningKeys jwtSigningKeys) {
        JWKSource<SecurityContext> jwkSource = new ImmutableJWKSet<>(new JWKSet(jwtSigningKeys.rsaKey()));
        return new NimbusJwtEncoder(jwkSource);
    }

    @Bean
    JwtDecoder jwtDecoder(JwtSigningKeys jwtSigningKeys, ApplicationProperties properties, Clock utcClock)
            throws JOSEException {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(jwtSigningKeys.rsaKey().toRSAPublicKey())
                .signatureAlgorithm(SIGNING_ALGORITHM)
                .build();
        decoder.setJwtValidator(tokenValidator(properties.jwt(), utcClock));
        return decoder;
    }

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(JwtConfiguration::authoritiesFromRolesClaim);
        converter.setPrincipalClaimName(JwtClaimNames.SUB);
        return converter;
    }

    static Collection<GrantedAuthority> authoritiesFromRolesClaim(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList(ROLES_CLAIM);
        if (roles == null || roles.isEmpty()) {
            return List.of();
        }
        return roles.stream()
                .filter(StringUtils::hasText)
                .map(String::strip)
                .map(JwtConfiguration::toAuthority)
                .distinct()
                .map(role -> (GrantedAuthority) new SimpleGrantedAuthority(role))
                .toList();
    }

    private static String toAuthority(String role) {
        return role.startsWith(AUTHORITY_PREFIX) ? role : AUTHORITY_PREFIX + role;
    }

    private static OAuth2TokenValidator<Jwt> tokenValidator(ApplicationProperties.Jwt jwt, Clock clock) {
        JwtTimestampValidator timestamps = new JwtTimestampValidator();
        timestamps.setClock(clock);
        OAuth2TokenValidator<Jwt> audience = new JwtClaimValidator<Collection<String>>(
                JwtClaimNames.AUD, audiences -> audiences != null && audiences.contains(jwt.audience()));
        return new DelegatingOAuth2TokenValidator<>(
                timestamps, new JwtIssuerValidator(jwt.issuer().toString()), audience);
    }
}
