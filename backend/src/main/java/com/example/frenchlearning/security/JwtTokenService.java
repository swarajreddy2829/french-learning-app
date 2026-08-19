package com.example.frenchlearning.security;

import com.example.frenchlearning.configuration.ApplicationProperties;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;

/**
 * Issues short-lived RS256 access tokens for an authenticated principal.
 *
 * <p>The subject is the user's public UUID. Role claims are the unprefixed names already granted to
 * the principal. Email addresses and password hashes are not included.
 */
@Service
public final class JwtTokenService {

    private final JwtEncoder jwtEncoder;
    private final ApplicationProperties.Jwt jwtProperties;
    private final Clock clock;

    public JwtTokenService(JwtEncoder jwtEncoder, ApplicationProperties properties, Clock clock) {
        this.jwtEncoder = Objects.requireNonNull(jwtEncoder, "jwtEncoder must not be null");
        this.jwtProperties = Objects.requireNonNull(properties, "properties must not be null").jwt();
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public String issueAccessToken(AuthenticatedUser user) {
        Objects.requireNonNull(user, "user must not be null");
        Instant issuedAt = Instant.now(clock);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(jwtProperties.issuer().toString())
                .audience(List.of(jwtProperties.audience()))
                .subject(user.getPublicId().toString())
                .issuedAt(issuedAt)
                .notBefore(issuedAt)
                .expiresAt(issuedAt.plus(jwtProperties.accessTokenTtl()))
                .id(UUID.randomUUID().toString())
                .claim(JwtConfiguration.ROLES_CLAIM, rolesFrom(user))
                .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    private static List<String> rolesFrom(AuthenticatedUser user) {
        return user.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.startsWith(JwtConfiguration.AUTHORITY_PREFIX))
                .map(authority -> authority.substring(JwtConfiguration.AUTHORITY_PREFIX.length()))
                .toList();
    }
}
