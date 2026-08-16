# Implementation Plan: French Learning Backend

**Branch**: `001-french-learning-backend` | **Date**: 2026-08-16 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/001-french-learning-backend/spec.md`

## Summary

Build a single deployable REST service for secure learner accounts, administrator-managed French lessons and quizzes, immutable quiz results, lesson completion, and personal progress. The implementation uses Java 21 and Spring Boot 3 with stateless JWT bearer authentication, method- and route-level role checks, PostgreSQL managed exclusively by Flyway, explicit DTO/mapper boundaries, RFC 9457-compatible errors, OpenAPI documentation, and layered automated tests against real PostgreSQL.

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: Latest supported Spring Boot 3 patch through its Maven BOM; Spring Web MVC; Spring Security; OAuth2 Resource Server with Nimbus JWT support; Spring Data JPA/Hibernate; Jakarta Bean Validation; Flyway; PostgreSQL JDBC; Spring Boot Actuator; SpringDoc OpenAPI WebMVC UI; Bouncy Castle for Argon2id; Jackson

**Storage**: PostgreSQL 16+; Flyway-owned relational schema; immutable published content revisions and quiz-attempt snapshots; UTC timestamps

**Testing**: JUnit 5, Mockito, AssertJ, Spring Boot Test, MockMvc, Spring Security Test, Testcontainers PostgreSQL, Flyway migration validation

**Target Platform**: Container-capable Linux server; local development supported on Windows, macOS, and Linux with Java 21, Maven Wrapper, and PostgreSQL

**Project Type**: Single Maven web service

**Performance Goals**: At least 95% of normal lesson, quiz, and progress requests complete within 2 seconds at 500 simultaneously active learners; quiz scoring and progress reconciliation remain exact

**Constraints**: Stateless access tokens; no refresh-token flow in v1; no JPA entities in public contracts; no schema auto-generation outside validation; all content and ownership changes transactional; historical attempts remain interpretable after content changes; secrets externalized; no internal diagnostics in client errors

**Scale/Scope**: Up to 100,000 registered users, 500 simultaneously active learners, a single curriculum, paginated catalogs and histories, one deployable service and one PostgreSQL database

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

The constitution file contains only unresolved template placeholders and has no ratified version, so it defines no enforceable project principles. The following provisional gates are derived from the approved feature specification:

- **Security boundary**: PASS — bearer-token validation is centralized; public registration cannot grant `ADMIN`; route checks are backed by service-level authorization; ownership is derived from the authenticated subject.
- **Separation of concerns**: PASS — controllers handle transport, application services coordinate use cases, domain rules stay outside controllers, repositories isolate persistence, and mappers isolate public DTOs from entities.
- **Data integrity and history**: PASS — Flyway owns DDL; constraints enforce race-sensitive invariants; published content and submitted attempts are immutable or retired rather than destructively changed.
- **Contract quality**: PASS — all external operations are defined in `contracts/openapi.yaml`; DTOs and RFC 9457-compatible problem responses form the public boundary.
- **Verification**: PASS — unit, repository/integration, API, validation, exception, authentication, and authorization tests are planned, with PostgreSQL integration tests using the production database engine.
- **Operational readiness**: PASS — configuration is externalized, health/readiness information is exposed safely, and the quickstart defines reproducible setup and validation.

**Pre-research result**: PASS (provisional because the project constitution is not ratified).

## Design Decisions

### Application boundaries

- Controllers depend on application services and DTO mappers, never directly on repositories.
- Application services define transaction boundaries, authorization-sensitive use cases, and orchestration.
- Domain models enforce local state rules such as publication, retirement, quiz validity, and attempt submission.
- Repositories expose task-focused queries and projections rather than leaking persistence behavior upward.
- Security components resolve the authenticated immutable user identifier; request payloads never choose the owner of learner data.

### Authentication and authorization

- Public registration creates only `USER`; administrators are provisioned operationally.
- Passwords use a delegating encoder with Argon2id as the current format, tuned on deployment hardware and capable of future upgrades.
- Login authenticates through Spring Security's authentication manager and issues a short-lived access token.
- Tokens contain `iss`, `aud`, immutable user ID in `sub`, `iat`, `nbf`, `exp`, `jti`, and minimal role claims. The signing algorithm is explicitly allowlisted.
- Resource-server support validates bearer tokens; no custom token-parsing filter is introduced.
- Route rules default to authenticated access, explicitly permitting only registration, login, and restricted-detail liveness/readiness probes; administrative service methods also require the `ADMIN` role.
- The token lifetime defaults to 15 minutes. Immediate revocation and refresh tokens are outside v1; disabling an account prevents new login, while short expiry bounds stale-token exposure.

### Persistence and consistency

- Stable lesson and quiz identities point to immutable published revisions. Administrative updates create a new draft/revision and atomically make it current.
- Retiring content removes it from learner catalogs while preserving revisions referenced by completions and attempts.
- Quiz submission is one transaction: validate ownership and revision, lock or atomically transition the attempt, record answers and snapshots, calculate score, and mark the attempt submitted.
- Unique, foreign-key, and check constraints enforce normalized email, ordering, one answer per question, answer-choice ownership, and one completion per user/lesson under concurrency.
- Progress is derived on read from completions and submitted attempts; no editable aggregate progress row is stored.
- Cursor pagination is used for attempt history; bounded offset pagination is acceptable for small content-management lists.

### Public contract and errors

- API base path is `/api/v1`.
- Successful bodies use a consistent `{ "data": ..., "meta": ... }` envelope; idempotent operations with no body return `204`.
- Errors use `application/problem+json` with RFC 9457 fields plus stable `code`, correlation `traceId`, and optional `fieldErrors`.
- Security-filter failures and controller failures serialize the same problem shape.
- OpenAPI documents bearer authentication globally and explicitly marks registration, login, and liveness as public.

## Project Structure

### Documentation (this feature)

```text
specs/001-french-learning-backend/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── openapi.yaml
└── tasks.md
```

### Source Code (repository root)

```text
pom.xml
compose.yaml
README.md
src/
├── main/
│   ├── java/com/example/frenchlearning/
│   │   ├── FrenchLearningApplication.java
│   │   ├── auth/
│   │   │   ├── controller/
│   │   │   ├── dto/
│   │   │   └── service/
│   │   ├── user/
│   │   │   ├── domain/
│   │   │   ├── repository/
│   │   │   └── service/
│   │   ├── lesson/
│   │   │   ├── controller/
│   │   │   ├── domain/
│   │   │   ├── dto/
│   │   │   ├── mapper/
│   │   │   ├── repository/
│   │   │   └── service/
│   │   ├── quiz/
│   │   │   ├── controller/
│   │   │   ├── domain/
│   │   │   ├── dto/
│   │   │   ├── mapper/
│   │   │   ├── repository/
│   │   │   └── service/
│   │   ├── progress/
│   │   │   ├── controller/
│   │   │   ├── dto/
│   │   │   ├── repository/
│   │   │   └── service/
│   │   ├── security/
│   │   ├── exception/
│   │   ├── configuration/
│   │   └── common/
│   │       ├── api/
│   │       └── persistence/
│   └── resources/
│       ├── application.yml
│       └── db/migration/
└── test/
    └── java/com/example/frenchlearning/
        ├── unit/
        ├── integration/
        ├── api/
        └── security/
```

**Structure Decision**: Use one Spring Boot Maven module organized first by business capability and then by layer. This preserves clear controller/service/repository/domain/DTO/mapper boundaries without creating premature multi-module build complexity. Shared transport and persistence utilities remain small and explicit under `common`.

## Phase 0: Research Outcome

Research is consolidated in [research.md](./research.md). All technical-context choices are resolved; no `NEEDS CLARIFICATION` items remain.

## Phase 1: Design Outcome

- [data-model.md](./data-model.md) defines entities, fields, constraints, relationships, indexes, transactions, and state transitions.
- [contracts/openapi.yaml](./contracts/openapi.yaml) defines the versioned REST contract, security scheme, success envelopes, pagination, validation, and errors.
- [quickstart.md](./quickstart.md) defines repeatable local setup and end-to-end acceptance validation.

## Post-Design Constitution Re-check

- **Security boundary**: PASS — every learner-owned endpoint uses authenticated subject ownership, while admin operations require role enforcement in both web and service boundaries.
- **Separation of concerns**: PASS — the data model and contract do not expose persistence entities; feature-oriented packages retain explicit layers.
- **Data integrity and history**: PASS — revision references, snapshots, retirement, constraints, and transactional attempt submission preserve history and concurrent correctness.
- **Contract quality**: PASS — the OpenAPI contract covers public and administrative workflows with a single envelope and problem schema.
- **Verification**: PASS — the quickstart maps executable checks to all P1/P2 journeys and security boundaries.
- **Operational readiness**: PASS — database migration, health checks, externalized secrets, Maven Wrapper, and containerized local PostgreSQL are included.

**Post-design result**: PASS (provisional until a project constitution is ratified). No complexity violations require justification.
