package com.example.frenchlearning.unit.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.frenchlearning.auth.service.EmailNormalizer;
import com.example.frenchlearning.auth.service.PasswordPolicy;
import com.example.frenchlearning.auth.service.RegistrationService;
import com.example.frenchlearning.exception.ConflictException;
import com.example.frenchlearning.exception.ErrorCode;
import com.example.frenchlearning.user.domain.Role;
import com.example.frenchlearning.user.domain.RoleName;
import com.example.frenchlearning.user.domain.User;
import com.example.frenchlearning.user.repository.RoleRepository;
import com.example.frenchlearning.user.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class RegistrationServiceTest {

    private static final String SUPPLIED_EMAIL = " Learner@Example.Test ";
    private static final String NORMALIZED_EMAIL = "learner@example.test";
    private static final String PASSWORD = "Learner-local-123!";
    private static final String PASSWORD_HASH = "{argon2}protected-value";

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private EmailNormalizer emailNormalizer;

    @Mock
    private PasswordPolicy passwordPolicy;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private Role userRole;

    @Mock
    private User persistedUser;

    private RegistrationService registrationService;

    @BeforeEach
    void setUp() {
        registrationService = new RegistrationService(
                userRepository,
                roleRepository,
                emailNormalizer,
                passwordPolicy,
                passwordEncoder);
    }

    @Test
    void registersANewLearnerWithNormalizedEmailProtectedPasswordAndUserRole() {
        when(emailNormalizer.normalize(SUPPLIED_EMAIL)).thenReturn(NORMALIZED_EMAIL);
        when(userRepository.existsByNormalizedEmail(NORMALIZED_EMAIL)).thenReturn(false);
        when(passwordEncoder.encode(PASSWORD)).thenReturn(PASSWORD_HASH);
        when(roleRepository.findByName(RoleName.USER)).thenReturn(Optional.of(userRole));
        when(userRepository.save(any(User.class))).thenReturn(persistedUser);

        User result = registrationService.register(SUPPLIED_EMAIL, PASSWORD);

        assertThat(result).isSameAs(persistedUser);
        verify(passwordPolicy).validate(PASSWORD);
        verify(passwordEncoder).encode(PASSWORD);
        verify(roleRepository).findByName(RoleName.USER);
        verify(userRepository).save(any(User.class));
    }

    @Test
    void rejectsAnAlreadyRegisteredNormalizedEmailWithStableConflictCode() {
        when(emailNormalizer.normalize(SUPPLIED_EMAIL)).thenReturn(NORMALIZED_EMAIL);
        when(userRepository.existsByNormalizedEmail(NORMALIZED_EMAIL)).thenReturn(true);

        assertThatThrownBy(() -> registrationService.register(SUPPLIED_EMAIL, PASSWORD))
                .isInstanceOfSatisfying(ConflictException.class, exception -> {
                    assertThat(exception.getErrorCode())
                            .isEqualTo(ErrorCode.EMAIL_ALREADY_REGISTERED);
                    assertThat(exception.getMessage()).isEqualTo("Email is already registered");
                });

        verify(userRepository, never()).save(any(User.class));
        verifyNoInteractions(roleRepository, passwordPolicy, passwordEncoder);
    }
}
