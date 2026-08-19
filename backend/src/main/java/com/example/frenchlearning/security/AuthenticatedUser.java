package com.example.frenchlearning.security;

import com.example.frenchlearning.user.domain.Role;
import com.example.frenchlearning.user.domain.RoleName;
import com.example.frenchlearning.user.domain.User;
import com.example.frenchlearning.user.domain.UserRole;
import com.example.frenchlearning.user.domain.UserStatus;
import java.io.Serial;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Spring Security principal for a persisted account.
 *
 * <p>{@link #getUsername()} is the normalized email used for lookup. {@link #getPublicId()} is the
 * immutable external identity later placed in token subjects. The password value is the stored
 * protected hash, never a raw credential, and is omitted from {@link #toString()}.
 */
public final class AuthenticatedUser implements UserDetails, CredentialsContainer {

    @Serial
    private static final long serialVersionUID = 1L;

    private final UUID publicId;
    private final String username;
    private final String email;
    private final Set<GrantedAuthority> authorities;
    private final boolean enabled;
    private final boolean accountNonLocked;
    private String password;

    public static AuthenticatedUser from(User user) {
        Objects.requireNonNull(user, "user must not be null");
        return new AuthenticatedUser(
                user.getPublicId(),
                user.getNormalizedEmail(),
                user.getEmail(),
                user.getPasswordHash(),
                authoritiesFrom(user),
                user.getStatus());
    }

    AuthenticatedUser(
            UUID publicId,
            String username,
            String email,
            String password,
            Collection<? extends GrantedAuthority> authorities,
            UserStatus status) {
        this.publicId = Objects.requireNonNull(publicId, "publicId must not be null");
        this.username = requireText(username, "username");
        this.email = requireText(email, "email");
        this.password = requireText(password, "password");
        this.authorities = Collections.unmodifiableSet(
                new LinkedHashSet<>(Objects.requireNonNull(authorities, "authorities")));
        Objects.requireNonNull(status, "status must not be null");
        this.enabled = status != UserStatus.DISABLED;
        this.accountNonLocked = status != UserStatus.LOCKED;
    }

    public UUID getPublicId() {
        return publicId;
    }

    public String getEmail() {
        return email;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return accountNonLocked;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void eraseCredentials() {
        this.password = null;
    }

    @Override
    public String toString() {
        return "AuthenticatedUser[publicId=%s, username=%s, email=%s, authorities=%s, enabled=%s, accountNonLocked=%s]"
                .formatted(publicId, username, email, authorities, enabled, accountNonLocked);
    }

    private static Set<GrantedAuthority> authoritiesFrom(User user) {
        return user.getRoles().stream()
                .map(UserRole::getRole)
                .map(Role::getName)
                .map(AuthenticatedUser::authority)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    static GrantedAuthority authority(RoleName roleName) {
        Objects.requireNonNull(roleName, "roleName must not be null");
        return new SimpleGrantedAuthority("ROLE_" + roleName.name());
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
