package com.example.frenchlearning.security;

import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Registers the production password encoder.
 *
 * <p>New passwords are encoded as Argon2id through a {@link DelegatingPasswordEncoder} so stored
 * values keep a stable {@code {id}} prefix and can be upgraded after successful authentication.
 * Parameters start at OWASP's Argon2id baseline. Unknown or malformed encoder IDs keep Spring
 * Security's default rejection behavior.
 */
@Configuration(proxyBeanMethods = false)
public class PasswordConfiguration {

    static final String ENCODING_ID = "argon2";

    /** OWASP Password Storage Cheat Sheet Argon2id baseline: 19 MiB. */
    static final int MEMORY_KIB = 19_456;

    /** OWASP Password Storage Cheat Sheet Argon2id baseline. */
    static final int ITERATIONS = 2;

    /** OWASP Password Storage Cheat Sheet Argon2id baseline. */
    static final int PARALLELISM = 1;

    /** Spring Security Argon2 v5.8 documented salt length. */
    static final int SALT_LENGTH_BYTES = 16;

    /** Spring Security Argon2 v5.8 documented hash length. */
    static final int HASH_LENGTH_BYTES = 32;

    @Bean
    PasswordEncoder passwordEncoder() {
        Argon2PasswordEncoder argon2 = new Argon2PasswordEncoder(
                SALT_LENGTH_BYTES, HASH_LENGTH_BYTES, PARALLELISM, MEMORY_KIB, ITERATIONS);
        Map<String, PasswordEncoder> encoders = Map.of(
                ENCODING_ID, argon2, "bcrypt", new BCryptPasswordEncoder());
        return new DelegatingPasswordEncoder(ENCODING_ID, encoders);
    }
}
