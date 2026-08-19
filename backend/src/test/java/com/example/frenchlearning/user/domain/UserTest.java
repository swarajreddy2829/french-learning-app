package com.example.frenchlearning.user.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class UserTest {

    private static final String EMAIL = "Learner@Example.Test";
    private static final String NORMALIZED_EMAIL = "learner@example.test";
    private static final String PASSWORD_HASH = "{argon2}protected-value";

    @Test
    void registerCreatesAnActiveUserWithOnlyTheUserRole() {
        Role userRole = new Role((short) 1, RoleName.USER);

        User user = User.register(EMAIL, NORMALIZED_EMAIL, PASSWORD_HASH, userRole);

        assertThat(user.getPublicId()).isNotNull();
        assertThat(user.getEmail()).isEqualTo(EMAIL);
        assertThat(user.getNormalizedEmail()).isEqualTo(NORMALIZED_EMAIL);
        assertThat(user.getPasswordHash()).isEqualTo(PASSWORD_HASH);
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.getRoles())
                .extracting(userRoleGrant -> userRoleGrant.getRole().getName())
                .containsExactly(RoleName.USER);
    }

    @Test
    void registerRejectsANonUserRole() {
        Role adminRole = new Role((short) 2, RoleName.ADMIN);

        assertThatThrownBy(
                        () -> User.register(EMAIL, NORMALIZED_EMAIL, PASSWORD_HASH, adminRole))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("public registration requires the USER role");
    }
}
