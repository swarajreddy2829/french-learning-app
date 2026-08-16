package com.example.frenchlearningtests.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.frenchlearning.FrenchLearningApplication;
import com.example.frenchlearning.common.persistence.AuditedEntity;
import com.example.frenchlearning.integration.PostgresIntegrationTest;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.RollbackException;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;

@SpringBootTest(
        classes = FrenchLearningApplication.class,
        properties = {
            "spring.flyway.enabled=false",
            "spring.jpa.hibernate.ddl-auto=create-drop"
        })
@Import(AuditedEntityIT.TestEntityConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AuditedEntityIT extends PostgresIntegrationTest {

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Test
    void populatesUtcAuditTimestampsAndAdvancesVersion() throws InterruptedException {
        AuditTestEntity persisted = inTransaction(entityManager -> {
            AuditTestEntity entity = new AuditTestEntity("created");
            entityManager.persist(entity);
            entityManager.flush();
            return entity;
        });
        AuditTestEntity storedAfterCreation = inTransaction(
                entityManager -> entityManager.find(AuditTestEntity.class, persisted.id));

        Instant createdAt = storedAfterCreation.getCreatedAt();
        Instant initiallyUpdatedAt = storedAfterCreation.getUpdatedAt();
        long initialVersion = storedAfterCreation.getVersion();

        Thread.sleep(10);

        AuditTestEntity updated = inTransaction(entityManager -> {
            AuditTestEntity entity = entityManager.find(AuditTestEntity.class, persisted.id);
            entity.name = "updated";
            entityManager.flush();
            return entity;
        });

        assertThat(createdAt).isNotNull();
        assertThat(initiallyUpdatedAt).isNotNull();
        assertThat(createdAt.atOffset(ZoneOffset.UTC).getOffset()).isEqualTo(ZoneOffset.UTC);
        assertThat(updated.getCreatedAt()).isEqualTo(createdAt);
        assertThat(updated.getUpdatedAt()).isAfter(initiallyUpdatedAt);
        assertThat(updated.getVersion()).isEqualTo(initialVersion + 1);
    }

    @Test
    void rejectsAnUpdateUsingAStaleOptimisticLockVersion() {
        Long id = inTransaction(entityManager -> {
            AuditTestEntity entity = new AuditTestEntity("original");
            entityManager.persist(entity);
            entityManager.flush();
            return entity.id;
        });

        EntityManager firstEntityManager = entityManagerFactory.createEntityManager();
        EntityManager staleEntityManager = entityManagerFactory.createEntityManager();
        EntityTransaction firstTransaction = firstEntityManager.getTransaction();
        EntityTransaction staleTransaction = staleEntityManager.getTransaction();

        try {
            firstTransaction.begin();
            staleTransaction.begin();
            AuditTestEntity firstCopy = firstEntityManager.find(AuditTestEntity.class, id);
            AuditTestEntity staleCopy = staleEntityManager.find(AuditTestEntity.class, id);

            firstCopy.name = "first update";
            firstTransaction.commit();

            staleCopy.name = "stale update";
            assertThatThrownBy(staleTransaction::commit)
                    .isInstanceOf(RollbackException.class)
                    .hasCauseInstanceOf(OptimisticLockException.class);
        } finally {
            if (firstTransaction.isActive()) {
                firstTransaction.rollback();
            }
            if (staleTransaction.isActive()) {
                staleTransaction.rollback();
            }
            firstEntityManager.close();
            staleEntityManager.close();
        }

        AuditTestEntity stored =
                inTransaction(entityManager -> entityManager.find(AuditTestEntity.class, id));
        assertThat(stored.name).isEqualTo("first update");
        assertThat(stored.getVersion()).isEqualTo(1);
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

    @TestConfiguration(proxyBeanMethods = false)
    @EntityScan(basePackageClasses = AuditTestEntity.class)
    static class TestEntityConfiguration {}

    @Entity(name = "AuditTestEntity")
    @Table(name = "audit_test_entity")
    static class AuditTestEntity extends AuditedEntity {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        private String name;

        protected AuditTestEntity() {}

        AuditTestEntity(String name) {
            this.name = name;
        }
    }
}
