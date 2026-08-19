package com.example.frenchlearning.integration.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.frenchlearning.common.persistence.AuditedEntity;
import com.example.frenchlearning.integration.PostgresIntegrationTest;
import com.example.frenchlearning.user.domain.Role;
import com.example.frenchlearning.user.domain.RoleName;
import com.example.frenchlearning.user.domain.User;
import com.example.frenchlearning.user.domain.UserRole;
import com.example.frenchlearning.user.domain.UserStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.PersistenceException;
import java.time.ZoneOffset;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class UserMappingIT extends PostgresIntegrationTest {

    private static final String PASSWORD_HASH = "{argon2}protected-value";

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Test
    void mapsUserRoleAndJoinTablesOntoTheFlywaySchema() {
        Long userId = inTransaction(entityManager -> {
            Role userRole = requiredRole(entityManager, RoleName.USER);
            Role adminRole = requiredRole(entityManager, RoleName.ADMIN);
            assertThat(adminRole.getId()).isEqualTo((short) 2);

            User user = User.register(
                    "Learner@Example.Test", "learner@example.test", PASSWORD_HASH, userRole);
            entityManager.persist(user);
            entityManager.flush();
            return user.getId();
        });

        User stored = inTransaction(entityManager -> entityManager
                .createQuery(
                        """
                        select distinct u from User u
                        join fetch u.roles ur
                        join fetch ur.role
                        where u.id = :id
                        """,
                        User.class)
                .setParameter("id", userId)
                .getSingleResult());

        assertThat(stored).isInstanceOf(AuditedEntity.class);
        assertThat(stored.getId()).isEqualTo(userId);
        assertThat(stored.getPublicId()).isNotNull();
        assertThat(stored.getEmail()).isEqualTo("Learner@Example.Test");
        assertThat(stored.getNormalizedEmail()).isEqualTo("learner@example.test");
        assertThat(stored.getPasswordHash()).isEqualTo(PASSWORD_HASH);
        assertThat(stored.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(stored.getCreatedAt()).isNotNull();
        assertThat(stored.getUpdatedAt()).isNotNull();
        assertThat(stored.getCreatedAt().atOffset(ZoneOffset.UTC).getOffset())
                .isEqualTo(ZoneOffset.UTC);
        assertThat(stored.getUpdatedAt()).isEqualTo(stored.getCreatedAt());
        assertThat(stored.getVersion()).isZero();
        assertThat(stored.getRoles())
                .extracting(UserRole::getRole)
                .extracting(Role::getName)
                .containsExactly(RoleName.USER);
        assertThat(stored.getRoles())
                .extracting(UserRole::getGrantedAt)
                .doesNotContainNull();
    }

    @Test
    void rejectsASecondUserWithTheSameNormalizedEmail() {
        inTransaction(entityManager -> {
            entityManager.persist(User.register(
                    "first@example.test",
                    "duplicate@example.test",
                    PASSWORD_HASH,
                    requiredRole(entityManager, RoleName.USER)));
            entityManager.flush();
            return null;
        });

        assertThatThrownBy(() -> inTransaction(entityManager -> {
                    entityManager.persist(User.register(
                            "second@example.test",
                            "duplicate@example.test",
                            PASSWORD_HASH,
                            requiredRole(entityManager, RoleName.USER)));
                    entityManager.flush();
                    return null;
                }))
                .isInstanceOf(PersistenceException.class);
    }

    private Role requiredRole(EntityManager entityManager, RoleName roleName) {
        return entityManager
                .createQuery("select r from Role r where r.name = :name", Role.class)
                .setParameter("name", roleName)
                .getSingleResult();
    }

    private <T> T inTransaction(Function<EntityManager, T> work) {
        EntityManager entityManager = entityManagerFactory.createEntityManager();
        EntityTransaction transaction = entityManager.getTransaction();
        transaction.begin();
        try {
            T result = work.apply(entityManager);
            transaction.commit();
            return result;
        } catch (RuntimeException exception) {
            if (transaction.isActive()) {
                transaction.rollback();
            }
            throw exception;
        } finally {
            entityManager.close();
        }
    }
}
