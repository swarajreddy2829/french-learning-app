# Phase 0 Research: French Learning Backend

**Date**: 2026-08-16  
**Scope**: Resolve technical choices for the Java 21, Spring Boot 3, PostgreSQL backend defined in [spec.md](./spec.md).

## Platform and Dependency Management

**Decision**: Use Java 21, Maven Wrapper, and the latest supported Spring Boot 3 patch available when implementation begins. Import dependency versions through the Spring Boot parent/BOM and pin only dependencies outside that management.

**Rationale**: Java 21 is the requested long-term-support runtime. Staying on the supported Boot 3 line satisfies the stated stack while allowing security and maintenance patches. BOM ownership avoids incompatible Spring component versions.

**Alternatives considered**:

- Spring Boot 4: newer major line, but outside the explicit Spring Boot 3 requirement.
- Independently pinned Spring modules: rejected because it increases compatibility and patch-management risk.
- Gradle: capable, but Maven is explicitly required.

## Service Shape and Package Architecture

**Decision**: Build one Spring Boot web-service module, organized by feature (`auth`, `user`, `lesson`, `quiz`, `progress`) and then by controller, service, domain, repository, DTO, and mapper responsibilities.

**Rationale**: A modular monolith is sufficient for the expected scale, keeps transactions and authorization straightforward, and avoids distributed-system overhead. Feature-first packaging limits cross-domain coupling while retaining the requested layers.

**Alternatives considered**:

- Layer-first packaging across the whole service: simple initially, but feature boundaries become harder to maintain.
- Multiple Maven modules: useful when boundaries need independent builds, but unnecessary before code volume or team ownership justifies it.
- Microservices: rejected because the workload and ownership model do not justify network, deployment, and consistency complexity.

## JWT Authentication

**Decision**: Use Spring Security's OAuth2 Resource Server support with Nimbus `JwtEncoder`/`JwtDecoder`. Issue 15-minute access tokens containing `iss`, `aud`, immutable user ID in `sub`, `iat`, `nbf`, `exp`, `jti`, and minimal roles. Explicitly allowlist the signing algorithm and externalize key material.

**Rationale**: Native resource-server support implements standard bearer processing and validation without a custom parsing filter. Short-lived tokens bound stale authorization exposure. Immutable IDs avoid coupling identity to changeable email addresses.

**Alternatives considered**:

- JJWT plus a custom authentication filter: rejected because Spring's native stack already supplies the required standards integration.
- Long-lived access tokens: rejected due to revocation and stale-role risk.
- Refresh tokens: deferred because the specification excludes long-lived session management in v1; adding them correctly requires rotation, hashed server-side state, reuse detection, and revocation.
- Database lookup on every authenticated request: stronger immediate account-state enforcement, but defeats the intended stateless access-token design. Short expiry is the v1 trade-off.

## Token Signing

**Decision**: Design for asymmetric signing with an externally supplied private key and public verification key. Include a key identifier so rotation can retain old verification keys until issued tokens expire.

**Rationale**: Verification material cannot mint tokens, which reduces impact if exposed to documentation or additional verifier processes. It also supports future service separation.

**Alternatives considered**:

- HMAC: simpler for a single process, but every holder of the verification secret can issue tokens. It remains an acceptable deployment-specific fallback only when supplied as a high-entropy external secret.

## Password Storage

**Decision**: Use Spring Security's `DelegatingPasswordEncoder` with Argon2id as the current format and Bouncy Castle support. Start at no less than OWASP's baseline and benchmark deployment hardware toward an acceptable adaptive-hash verification time. Support encoding upgrades after successful authentication.

**Rationale**: Argon2id is memory-hard and recommended for new password storage. Delegating identifiers permit future work-factor or algorithm migration without invalidating existing accounts.

**Alternatives considered**:

- BCrypt: mature and operationally simple, but not memory-hard and has input-length caveats. It is the fallback if Argon2 operational requirements cannot be met.
- PBKDF2: appropriate for environments with specific compliance constraints, but not the default here.

## Authorization and Ownership

**Decision**: Convert token roles to `ROLE_USER` and `ROLE_ADMIN`, default all routes to authenticated, explicitly permit only registration, login, and restricted-detail liveness/readiness probes, and apply method authorization to administrative services. Resolve learner ownership solely from the token subject.

**Rationale**: Route checks provide a coarse boundary, service checks prevent accidental bypass, and subject-derived ownership prevents insecure direct-object-reference flaws.

**Alternatives considered**:

- Controller-only role checks: rejected because alternate callers could bypass them.
- User IDs in learner request paths or bodies: rejected for self-service operations because they create avoidable ownership ambiguity.

## Security and Error Integration

**Decision**: Use an RFC 9457-compatible `application/problem+json` schema for controller and security-filter failures. Implement a shared serializer used by `@RestControllerAdvice`, `AuthenticationEntryPoint`, and `AccessDeniedHandler`. Preserve `WWW-Authenticate` for bearer failures.

**Rationale**: Security failures occur before controller advice, so explicit filter-layer handling is required for one consistent external contract. Stable error codes support clients without exposing internal exceptions.

**Alternatives considered**:

- A custom non-standard error object: possible, but Problem Details supplies interoperable base semantics.
- Relying only on `@RestControllerAdvice`: rejected because it does not handle all filter-chain authentication and authorization failures.

## Persistence and Schema Evolution

**Decision**: Use PostgreSQL 16+ with Spring Data JPA/Hibernate. Flyway exclusively owns schema changes; Hibernate validates the schema at startup. Use immutable versioned migrations, production-like PostgreSQL integration tests, and controlled migration execution.

**Rationale**: PostgreSQL provides relational constraints and transaction behavior needed for ownership, quiz scoring, and concurrency. Flyway gives deterministic, reviewable evolution and avoids environment drift.

**Alternatives considered**:

- Liquibase: capable, but Flyway is simpler for SQL-first PostgreSQL migrations and only one migration tool is needed.
- Hibernate schema generation: rejected for production because it is not an auditable evolution strategy.
- H2 integration tests: rejected because SQL, constraints, locking, and type behavior differ from PostgreSQL.

## Content History

**Decision**: Separate stable `Lesson` and `Quiz` identities from immutable published revisions. Administrative edits create new revisions; retirement hides content from new learner activity. Attempts reference exact quiz revisions, and submitted answers preserve prompt/choice snapshots and correctness at submission.

**Rationale**: Historical performance must remain meaningful after content edits or retirement. Explicit revisions provide stronger auditability than trying to infer history from mutable rows.

**Alternatives considered**:

- Mutable content with scores only: simplest, but old question-level reviews would change meaning.
- Mutable content plus JSON snapshots only: workable, but relational revisions better preserve queryability and constraints. Small snapshots are still retained on submitted answers as defense against later migration or archival.
- Hard deletion with cascades: rejected because it would destroy or invalidate learner history.

## Transaction and Concurrency Strategy

**Decision**: Keep service transactions short at PostgreSQL `READ COMMITTED`. Use database constraints for race-sensitive invariants, optimistic versioning for mutable drafts, idempotent completion inserts, and a locked or atomic state transition for quiz submission.

**Rationale**: Constraints remain correct under concurrent requests. A single submission transaction prevents partial answers or double scoring without imposing serializable isolation on ordinary reads.

**Alternatives considered**:

- Check-then-insert in application code: rejected because concurrent requests can pass the same check.
- Global `SERIALIZABLE`: rejected due to unnecessary contention and retry complexity.
- Pessimistic locks for all editing: rejected because optimistic locking is sufficient for infrequent administrative conflicts.

## Identifiers, Time, and Controlled Values

**Decision**: Use generated `BIGINT` primary keys internally and UUID public identifiers for users and learning resources. Use UTC instants backed by `timestamptz`. Represent evolving statuses and difficulty levels as text with database checks and application enums.

**Rationale**: Compact internal keys keep joins and indexes efficient, while opaque public IDs reduce enumeration and decouple contracts from database cardinality. Text checks evolve more easily than PostgreSQL enum types.

**Alternatives considered**:

- UUID primary keys everywhere: valid but creates larger indexes without a distributed-ID requirement.
- Sequential public IDs: simpler, but exposes cardinality and makes resource enumeration easier.
- PostgreSQL enum types: strict, but more cumbersome to evolve through migrations.

## Querying, Indexing, and Pagination

**Decision**: Index normalized email, foreign keys used in joins, active lesson ordering, quiz revision/question ordering, user attempt history, and user completion history. Use deterministic cursor pagination for attempts and bounded offset pagination for small administrative catalogs.

**Rationale**: These indexes match required lookup and ownership paths. Cursor pagination avoids increasingly expensive deep offsets for append-heavy histories.

**Alternatives considered**:

- Index every filterable field: rejected due to write and maintenance cost.
- Offset pagination everywhere: acceptable for small lists, but unstable and costly for deep activity history.
- Partitioning: not justified at 100,000 users and 500 active learners; reconsider only after measured table growth reaches tens of millions of history rows.

## API Contract

**Decision**: Version routes under `/api/v1`, use purpose-specific DTOs, a `data`/`meta` success envelope, bounded pagination, and an OpenAPI 3.1 contract served through SpringDoc Swagger UI with bearer authentication.

**Rationale**: Versioning and DTOs protect clients from persistence changes. A consistent envelope supports metadata without exposing framework pagination types. OpenAPI provides executable documentation.

**Alternatives considered**:

- Exposing entities: rejected due to coupling, accidental sensitive fields, and serialization cycles.
- Bare responses: simpler, but inconsistent once pagination and metadata are introduced.
- GraphQL: not required for the resource-oriented workflows and adds unnecessary dependencies.

## Validation

**Decision**: Apply syntactic validation at DTO boundaries and business validation in services/domain models. Return all safely reportable field violations together; use database constraints as the final integrity layer.

**Rationale**: Each layer catches the class of invalid state it understands while preserving concurrency correctness. Validation messages remain actionable without leaking sensitive rules.

**Alternatives considered**:

- DTO validation only: cannot enforce current-state, ownership, or cross-record rules.
- Database validation only: correct but produces poor client feedback and late failures.

## Testing

**Decision**: Use JUnit 5, Mockito, AssertJ, Spring Boot Test, MockMvc, Spring Security Test, Testcontainers PostgreSQL, and Flyway validation. Keep fast service tests isolated, use slice tests where valuable, and reserve full-context tests for end-to-end API/security flows.

**Rationale**: The test pyramid gives quick business-logic feedback while real PostgreSQL tests verify migrations, constraints, queries, and transactions. MockMvc verifies the deployed HTTP/security contract without requiring a separate client stack.

**Alternatives considered**:

- Full-context tests for everything: rejected because they are slower and obscure unit-level failures.
- H2 repository tests: rejected due to behavioral mismatch.
- Mockito-only persistence tests: rejected because they cannot validate SQL, mappings, migrations, or constraints.

## Operational Readiness

**Decision**: Use externalized configuration, environment-injected signing keys and database credentials, structured logs with correlation IDs, Actuator liveness/readiness endpoints with restricted details, Maven Wrapper, and a local PostgreSQL Compose profile.

**Rationale**: These choices support repeatable setup and safe runtime diagnosis without embedding secrets or exposing internals.

**Alternatives considered**:

- Committed development secrets: rejected because examples are frequently reused unsafely.
- Public detailed health output: rejected because dependency and configuration details can aid attackers.
- Adding a full observability platform: deferred; the service should emit standard signals without imposing an unrelated operations stack.

## References

- [Spring Security password storage](https://docs.spring.io/spring-security/reference/features/authentication/password-storage.html)
- [Spring Security JWT resource server](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html)
- [Spring Security method authorization](https://docs.spring.io/spring-security/reference/servlet/authorization/method-security.html)
- [OWASP Password Storage Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html)
- [JWT Best Current Practices (RFC 8725)](https://www.rfc-editor.org/rfc/rfc8725.html)
- [Bearer Token Usage (RFC 6750)](https://www.rfc-editor.org/rfc/rfc6750.html)
- [Problem Details for HTTP APIs (RFC 9457)](https://www.rfc-editor.org/rfc/rfc9457.html)
- [PostgreSQL constraints](https://www.postgresql.org/docs/current/ddl-constraints.html)
- [PostgreSQL transaction isolation](https://www.postgresql.org/docs/current/transaction-iso.html)
- [Flyway migration transaction handling](https://documentation.red-gate.com/flyway/flyway-concepts/migrations/migration-transaction-handling)
