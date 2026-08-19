package com.example.frenchlearning.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.example.frenchlearning.auth.service.EmailNormalizer;
import com.example.frenchlearning.user.domain.Role;
import com.example.frenchlearning.user.domain.RoleName;
import com.example.frenchlearning.user.domain.User;
import com.example.frenchlearning.user.domain.UserRole;
import com.example.frenchlearning.user.domain.UserStatus;
import com.example.frenchlearning.user.repository.UserRepository;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

@ExtendWith(MockitoExtension.class)
class DatabaseUserDetailsServiceTest {

    private static final String SUPPLIED_EMAIL = " Learner@Example.Test ";
    private static final String NORMALIZED_EMAIL = "learner@example.test";
    private static final String DISPLAY_EMAIL = "Learner@Example.Test";
    private static final String PASSWORD_HASH = "{argon2}protected-value";
    private static final UUID PUBLIC_ID = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");

    @Mock
    private UserRepository userRepository;

    private DatabaseUserDetailsService userDetailsService;

    @BeforeEach
    void setUp() {
        userDetailsService = new DatabaseUserDetailsService(userRepository, new EmailNormalizer());
    }

    @Test
    void loadsAnActiveUserByNormalizedEmailAndMapsUserAuthority() {
        User account = user(UserStatus.ACTIVE, RoleName.USER);
        when(userRepository.findByNormalizedEmail(NORMALIZED_EMAIL)).thenReturn(Optional.of(account));

        AuthenticatedUser principal = userDetailsService.loadUserByUsername(SUPPLIED_EMAIL);

        assertThat(principal.getPublicId()).isEqualTo(PUBLIC_ID);
        assertThat(principal.getUsername()).isEqualTo(NORMALIZED_EMAIL);
        assertThat(principal.getEmail()).isEqualTo(DISPLAY_EMAIL);
        assertThat(principal.getPassword()).isEqualTo(PASSWORD_HASH);
        assertThat(principal.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_USER");
        assertThat(principal.isEnabled()).isTrue();
        assertThat(principal.isAccountNonLocked()).isTrue();
        verify(userRepository).findByNormalizedEmail(NORMALIZED_EMAIL);
        verifyNoMoreInteractions(userRepository);
    }

    @Test
    void mapsAdminAndUserRolesToSpringAuthorities() {
        User account = user(UserStatus.ACTIVE, RoleName.USER, RoleName.ADMIN);
        when(userRepository.findByNormalizedEmail(NORMALIZED_EMAIL)).thenReturn(Optional.of(account));

        AuthenticatedUser principal = userDetailsService.loadUserByUsername(SUPPLIED_EMAIL);

        assertThat(principal.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_USER", "ROLE_ADMIN");
    }

    @Test
    void exposesDisabledAndLockedSpringAccountFlags() {
        User disabledAccount = user(UserStatus.DISABLED, RoleName.USER);
        when(userRepository.findByNormalizedEmail(NORMALIZED_EMAIL)).thenReturn(Optional.of(disabledAccount));

        AuthenticatedUser disabled = userDetailsService.loadUserByUsername(SUPPLIED_EMAIL);
        assertThat(disabled.isEnabled()).isFalse();
        assertThat(disabled.isAccountNonLocked()).isTrue();

        User lockedAccount = user(UserStatus.LOCKED, RoleName.USER);
        when(userRepository.findByNormalizedEmail(NORMALIZED_EMAIL)).thenReturn(Optional.of(lockedAccount));

        AuthenticatedUser locked = userDetailsService.loadUserByUsername(SUPPLIED_EMAIL);
        assertThat(locked.isEnabled()).isTrue();
        assertThat(locked.isAccountNonLocked()).isFalse();
    }

    @Test
    void unknownOrUnusableUsernamesBecomeUserNotFoundWithoutLeakingSecrets() {
        when(userRepository.findByNormalizedEmail(NORMALIZED_EMAIL)).thenReturn(Optional.empty());

        assertNotFound(SUPPLIED_EMAIL);
        assertNotFound("   ");
        assertNotFound(null);
        verify(userRepository).findByNormalizedEmail(NORMALIZED_EMAIL);
    }

    private void assertNotFound(String username) {
        assertThatThrownBy(() -> userDetailsService.loadUserByUsername(username))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessage("User not found")
                .satisfies(exception -> {
                    assertThat(exception.getMessage()).doesNotContain(PASSWORD_HASH);
                    assertThat(exception.toString()).doesNotContain(PASSWORD_HASH);
                });
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
