package com.example.frenchlearning.integration.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.frenchlearning.integration.PostgresIntegrationTest;
import com.example.frenchlearning.user.domain.Role;
import com.example.frenchlearning.user.domain.RoleName;
import com.example.frenchlearning.user.domain.User;
import com.example.frenchlearning.user.domain.UserStatus;
import com.example.frenchlearning.user.repository.RoleRepository;
import com.example.frenchlearning.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

class UserRepositoryIT extends PostgresIntegrationTest {

    private static final String PASSWORD_HASH = "{argon2}protected-value";

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void deleteUsers() {
        userRepository.deleteAll();
    }

    @Test
    void storesAndFindsAUserByNormalizedEmail() {
        User saved = userRepository.saveAndFlush(newUser(
                "Learner@Example.Test", "learner@example.test", requiredRole(RoleName.USER)));
        entityManager.clear();

        User found = userRepository
                .findByNormalizedEmail("learner@example.test")
                .orElseThrow();

        assertThat(found.getId()).isEqualTo(saved.getId());
        assertThat(found.getEmail()).isEqualTo("Learner@Example.Test");
        assertThat(found.getNormalizedEmail()).isEqualTo("learner@example.test");
        assertThat(found.getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void enforcesNormalizedEmailUniquenessInPostgres() {
        Role userRole = requiredRole(RoleName.USER);
        userRepository.saveAndFlush(
                newUser("learner@example.test", "learner@example.test", userRole));

        assertThatThrownBy(() -> userRepository.saveAndFlush(
                        newUser("LEARNER@example.test", "learner@example.test", userRole)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void persistsOnlyTheUserRoleForPublicRegistration() {
        userRepository.saveAndFlush(
                newUser("learner@example.test", "learner@example.test", requiredRole(RoleName.USER)));
        entityManager.clear();

        User found = userRepository
                .findByNormalizedEmail("learner@example.test")
                .orElseThrow();

        assertThat(found.getRoles())
                .extracting(userRole -> userRole.getRole().getName())
                .containsExactly(RoleName.USER);
        assertThat(roleRepository.findByName(RoleName.ADMIN)).isPresent();
    }

    @Test
    void allowsExactlyOneOfTwoConcurrentNormalizedEmailInserts() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            List<Future<Boolean>> results = List.of(
                    executor.submit(() -> insertConcurrently(
                            "Learner@Example.Test", ready, start)),
                    executor.submit(() -> insertConcurrently(
                            "LEARNER@example.test", ready, start)));

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(List.of(
                            results.get(0).get(10, TimeUnit.SECONDS),
                            results.get(1).get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        }

        assertThat(userRepository.count()).isEqualTo(1);
        assertThat(userRepository.findByNormalizedEmail("learner@example.test")).isPresent();
    }

    private boolean insertConcurrently(
            String displayEmail, CountDownLatch ready, CountDownLatch start) throws Exception {
        Role role = requiredRole(RoleName.USER);
        User user = newUser(displayEmail, "learner@example.test", role);
        ready.countDown();
        assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
        try {
            userRepository.saveAndFlush(user);
            return true;
        } catch (DataIntegrityViolationException exception) {
            return false;
        }
    }

    private Role requiredRole(RoleName roleName) {
        return roleRepository.findByName(roleName).orElseThrow();
    }

    private User newUser(String email, String normalizedEmail, Role role) {
        return User.register(email, normalizedEmail, PASSWORD_HASH, role);
    }
}
