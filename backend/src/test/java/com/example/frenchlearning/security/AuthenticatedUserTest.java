package com.example.frenchlearning.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.frenchlearning.user.domain.Role;
import com.example.frenchlearning.user.domain.RoleName;
import com.example.frenchlearning.user.domain.User;
import com.example.frenchlearning.user.domain.UserRole;
import com.example.frenchlearning.user.domain.UserStatus;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;

class AuthenticatedUserTest {

    private static final UUID PUBLIC_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final String DISPLAY_EMAIL = "Learner@Example.Test";
    private static final String NORMALIZED_EMAIL = "learner@example.test";
    private static final String PASSWORD_HASH = "{argon2}protected-value";

    @Test
    void fromActiveUserExposesIdentityUserAuthorityAndUsableAccountFlags() {
        AuthenticatedUser principal = AuthenticatedUser.from(user(UserStatus.ACTIVE, RoleName.USER));

        assertThat(principal.getPublicId()).isEqualTo(PUBLIC_ID);
        assertThat(principal.getUsername()).isEqualTo(NORMALIZED_EMAIL);
        assertThat(principal.getEmail()).isEqualTo(DISPLAY_EMAIL);
        assertThat(principal.getPassword()).isEqualTo(PASSWORD_HASH);
        assertThat(authorities(principal)).containsExactly("ROLE_USER");
        assertThat(principal.isEnabled()).isTrue();
        assertThat(principal.isAccountNonLocked()).isTrue();
        assertThat(principal.isAccountNonExpired()).isTrue();
        assertThat(principal.isCredentialsNonExpired()).isTrue();
        assertThat(principal.toString())
                .contains(PUBLIC_ID.toString(), NORMALIZED_EMAIL, DISPLAY_EMAIL, "ROLE_USER")
                .doesNotContain(PASSWORD_HASH);
    }

    @Test
    void mapsEveryAssignedRoleToAPrefixedSpringAuthority() {
        AuthenticatedUser principal =
                AuthenticatedUser.from(user(UserStatus.ACTIVE, RoleName.USER, RoleName.ADMIN));

        assertThat(authorities(principal)).containsExactly("ROLE_USER", "ROLE_ADMIN");
    }

    @Test
    void disabledAccountsAreNotEnabledAndRemainUnlocked() {
        AuthenticatedUser principal = AuthenticatedUser.from(user(UserStatus.DISABLED, RoleName.USER));

        assertThat(principal.isEnabled()).isFalse();
        assertThat(principal.isAccountNonLocked()).isTrue();
        assertThat(principal.getPassword()).isEqualTo(PASSWORD_HASH);
    }

    @Test
    void lockedAccountsRemainEnabledAndAreNotNonLocked() {
        AuthenticatedUser principal = AuthenticatedUser.from(user(UserStatus.LOCKED, RoleName.USER));

        assertThat(principal.isEnabled()).isTrue();
        assertThat(principal.isAccountNonLocked()).isFalse();
        assertThat(principal.getPassword()).isEqualTo(PASSWORD_HASH);
    }

    @Test
    void eraseCredentialsRemovesThePasswordHashFromThePrincipal() {
        AuthenticatedUser principal = AuthenticatedUser.from(user(UserStatus.ACTIVE, RoleName.USER));

        principal.eraseCredentials();

        assertThat(principal.getPassword()).isNull();
        assertThat(principal.toString()).doesNotContain(PASSWORD_HASH);
    }

    private static List<String> authorities(AuthenticatedUser principal) {
        return principal.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
    }

    private static User user(UserStatus status, RoleName... roles) {
        Set<UserRole> grants = new LinkedHashSet<>();
        for (RoleName roleName : roles) {
            Role role = mock(Role.class);
            when(role.getName()).thenReturn(roleName);
            UserRole grant = mock(UserRole.class);
            when(grant.getRole()).thenReturn(role);
            grants.add(grant);
        }

        User user = mock(User.class);
        when(user.getPublicId()).thenReturn(PUBLIC_ID);
        when(user.getEmail()).thenReturn(DISPLAY_EMAIL);
        when(user.getNormalizedEmail()).thenReturn(NORMALIZED_EMAIL);
        when(user.getPasswordHash()).thenReturn(PASSWORD_HASH);
        when(user.getStatus()).thenReturn(status);
        when(user.getRoles()).thenReturn(grants);
        return user;
    }
}
