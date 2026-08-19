package com.example.frenchlearning.security;

import com.example.frenchlearning.auth.service.EmailNormalizer;
import com.example.frenchlearning.user.domain.User;
import com.example.frenchlearning.user.repository.UserRepository;
import java.util.Objects;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Loads persisted accounts for Spring Security authentication.
 *
 * <p>Lookup uses locale-independent normalized email. Password matching is left to Spring Security;
 * this service only exposes the stored protected hash on the returned principal.
 */
@Service
public class DatabaseUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final EmailNormalizer emailNormalizer;

    public DatabaseUserDetailsService(UserRepository userRepository, EmailNormalizer emailNormalizer) {
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository must not be null");
        this.emailNormalizer = Objects.requireNonNull(emailNormalizer, "emailNormalizer must not be null");
    }

    @Override
    @Transactional(readOnly = true)
    public AuthenticatedUser loadUserByUsername(String username) {
        String normalizedEmail = normalizeForLookup(username);
        User user = userRepository
                .findByNormalizedEmail(normalizedEmail)
                .orElseThrow(DatabaseUserDetailsService::userNotFound);
        return AuthenticatedUser.from(user);
    }

    private String normalizeForLookup(String username) {
        try {
            return emailNormalizer.normalize(username);
        } catch (RuntimeException ex) {
            throw userNotFound(ex);
        }
    }

    private static UsernameNotFoundException userNotFound() {
        return new UsernameNotFoundException("User not found");
    }

    private static UsernameNotFoundException userNotFound(Throwable cause) {
        return new UsernameNotFoundException("User not found", cause);
    }
}
