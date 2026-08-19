package com.example.frenchlearning.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class PasswordConfigurationTest {

    private static final String RAW_PASSWORD = "Learner-local-123!";

    private final PasswordEncoder passwordEncoder = new PasswordConfiguration().passwordEncoder();

    @Test
    void encodesWithDelegatingArgon2idPrefixAndOwaspBaselineParameters() {
        String encoded = passwordEncoder.encode(RAW_PASSWORD);

        assertThat(encoded).startsWith("{argon2}");
        assertThat(encoded).contains("$argon2id$");
        assertThat(encoded).contains("m=19456,t=2,p=1");
        assertThat(encoded).doesNotContain(RAW_PASSWORD);
        assertThat(encoded.length()).isLessThanOrEqualTo(255);
        assertThat(passwordEncoder.upgradeEncoding(encoded)).isFalse();
    }

    @Test
    void encodesTheSamePasswordToDistinctSaltedHashesThatBothMatch() {
        String first = passwordEncoder.encode(RAW_PASSWORD);
        String second = passwordEncoder.encode(RAW_PASSWORD);

        assertThat(first).isNotEqualTo(second);
        assertThat(passwordEncoder.matches(RAW_PASSWORD, first)).isTrue();
        assertThat(passwordEncoder.matches(RAW_PASSWORD, second)).isTrue();
        assertThat(passwordEncoder.matches("Wrong-password-123!", first)).isFalse();
        assertThat(first).doesNotContain(RAW_PASSWORD);
        assertThat(second).doesNotContain(RAW_PASSWORD);
    }

    @Test
    void matchesKnownBcryptHashesAndMarksThemForUpgrade() {
        String bcryptHash = "{bcrypt}" + new BCryptPasswordEncoder().encode(RAW_PASSWORD);

        assertThat(passwordEncoder.matches(RAW_PASSWORD, bcryptHash)).isTrue();
        assertThat(passwordEncoder.matches("Wrong-password-123!", bcryptHash)).isFalse();
        assertThat(passwordEncoder.upgradeEncoding(bcryptHash)).isTrue();
        assertThat(bcryptHash).doesNotContain(RAW_PASSWORD);
    }

    @Test
    void upgradesWeakerArgon2ParametersAfterASuccessfulMatch() {
        String weakerHash = "{argon2}"
                + Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8().encode(RAW_PASSWORD);

        assertThat(weakerHash).contains("m=16384,t=2,p=1");
        assertThat(passwordEncoder.matches(RAW_PASSWORD, weakerHash)).isTrue();
        assertThat(passwordEncoder.upgradeEncoding(weakerHash)).isTrue();
    }

    @Test
    void rejectsUnknownAndMalformedEncoderIdsWithoutLeakingThePassword() {
        assertUnmapped("{unknown}not-a-real-hash");
        assertUnmapped("not-prefixed-hash");
        assertUnmapped("{incomplete");
        assertThat(passwordEncoder.matches(RAW_PASSWORD, "{argon2}not-a-valid-argon2-hash"))
                .isFalse();
    }

    private void assertUnmapped(String encodedPassword) {
        assertThatThrownBy(() -> passwordEncoder.matches(RAW_PASSWORD, encodedPassword))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageNotContaining(RAW_PASSWORD)
                .satisfies(exception -> assertThat(exception.toString()).doesNotContain(RAW_PASSWORD));
    }
}
