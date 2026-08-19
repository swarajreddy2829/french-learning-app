# Tasks: French Learning Backend

**Input**: Design documents from `/specs/001-french-learning-backend/`

**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/openapi.yaml`, `quickstart.md`

**Tests**: Automated tests are required by the feature specification. Within every user-story phase, create the listed tests first and confirm they fail for the expected reason before implementing the story.

**Organization**: Tasks are grouped by user story so that each story has an independently testable outcome. Paths use the single Maven project layout from `plan.md`.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel after its phase prerequisites because it changes different files and does not depend on another incomplete task in the same group.
- **[Story]**: Maps the task to one specification user story.
- Every task includes an exact target path.

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Initialize the Java 21/Spring Boot 3 Maven service and repeatable local environment.

- [X] T001 Create `pom.xml` with Java 21, the latest supported Spring Boot 3 parent, and the Web, Validation, Security, OAuth2 Resource Server, Data JPA, PostgreSQL, Flyway PostgreSQL, Actuator, SpringDoc, Bouncy Castle, Boot Test, Security Test, and Testcontainers PostgreSQL dependencies
- [X] T002 Create Maven Wrapper files `mvnw`, `mvnw.cmd`, `.mvn/wrapper/maven-wrapper.jar`, and `.mvn/wrapper/maven-wrapper.properties`, then add the application entry point at `src/main/java/com/example/frenchlearning/FrenchLearningApplication.java`
- [X] T003 [P] Create feature package boundaries with documentation in `src/main/java/com/example/frenchlearning/auth/package-info.java`, `user/package-info.java`, `lesson/package-info.java`, `quiz/package-info.java`, `progress/package-info.java`, `security/package-info.java`, `exception/package-info.java`, `configuration/package-info.java`, and `common/package-info.java`
- [X] T004 [P] Configure externalized defaults and `local`/`test` profiles in `src/main/resources/application.yml`, `src/main/resources/application-local.yml`, and `src/test/resources/application-test.yml`
- [X] T005 [P] Create local PostgreSQL configuration and safe examples in `compose.yaml`, `.env.example`, and `.gitignore`, excluding `.env`, `.local/`, private keys, generated output, and IDE metadata
- [X] T006 Configure compiler, Surefire, Failsafe, JaCoCo, and build reproducibility rules in `pom.xml`, separating `*Test` unit tests from `*IT` integration tests
- [X] T007 [P] Create the initial setup, configuration, build, and run outline in `README.md` with links to `specs/001-french-learning-backend/quickstart.md` and `specs/001-french-learning-backend/contracts/openapi.yaml`

**Checkpoint**: `.\mvnw.cmd test` starts a Spring Boot test context, and local PostgreSQL can be started independently.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Build shared persistence, configuration, API, error, logging, and test infrastructure required by every story.

**⚠️ CRITICAL**: No user-story implementation begins until this phase is complete.

- [X] T008 Create a reusable PostgreSQL Testcontainers base with dynamic datasource properties in `src/test/java/com/example/frenchlearning/integration/PostgresIntegrationTest.java`
- [X] T009 [P] Add application context and production-profile configuration smoke tests in `src/test/java/com/example/frenchlearning/integration/ApplicationContextIT.java`
- [X] T010 [P] Enable JPA auditing and implement UTC audit fields plus optimistic locking in `src/main/java/com/example/frenchlearning/configuration/PersistenceConfiguration.java` and `src/main/java/com/example/frenchlearning/common/persistence/AuditedEntity.java`
- [X] T011 [P] Implement validated application, database, JWT, pagination, and bootstrap configuration properties in `src/main/java/com/example/frenchlearning/configuration/ApplicationProperties.java`
- [X] T012 [P] Implement success envelopes and page/cursor metadata in `src/main/java/com/example/frenchlearning/common/api/ApiResponse.java`, `PageMeta.java`, and `CursorMeta.java`
- [X] T013 [P] Define stable error codes, field violations, and RFC 9457 extension names in `src/main/java/com/example/frenchlearning/exception/ErrorCode.java` and `src/main/java/com/example/frenchlearning/exception/FieldViolation.java`
- [X] T014 Implement domain exception types and centralized validation, malformed request, conflict, not-found, and fallback handling in `src/main/java/com/example/frenchlearning/exception/ApiException.java`, `ResourceNotFoundException.java`, `ConflictException.java`, and `GlobalExceptionHandler.java`
- [X] T015 [P] Add correlation ID propagation and safe structured request logging in `src/main/java/com/example/frenchlearning/configuration/CorrelationIdFilter.java` and `src/main/java/com/example/frenchlearning/configuration/LoggingConfiguration.java`
- [X] T016 [P] Implement bounded page validation and opaque attempt cursor encoding/decoding in `src/main/java/com/example/frenchlearning/common/api/PageRequestValidator.java` and `src/main/java/com/example/frenchlearning/common/api/AttemptCursorCodec.java`
- [X] T017 Configure restricted-detail liveness/readiness groups and verify them in `src/main/resources/application.yml` and `src/test/java/com/example/frenchlearning/integration/HealthEndpointIT.java`

**Checkpoint**: Shared infrastructure compiles, PostgreSQL-backed tests can run, and the standard API/error primitives are available to all stories.

---

## Phase 3: User Story 1 - Register and Sign In Securely (Priority: P1) 🎯 MVP

**Goal**: A learner can register and authenticate, receive a short-lived JWT, use it on protected routes, and receive consistent failures for duplicate registration or invalid/expired credentials.

**Independent Test**: Register a new email, log in, use the token on a protected test endpoint, and verify duplicate email, missing token, malformed token, expired token, wrong password, and non-existent account behavior.

### Tests for User Story 1

- [ ] T018 [P] [US1] Write failing registration and duplicate-email service tests in `src/test/java/com/example/frenchlearning/unit/auth/RegistrationServiceTest.java`
- [X] T019 [P] [US1] Write failing user repository normalization, uniqueness, role, and concurrent-registration tests in `src/test/java/com/example/frenchlearning/integration/user/UserRepositoryIT.java`
- [X] T020 [P] [US1] Write failing registration/login request, response, validation, and generic-credential-error API tests in `src/test/java/com/example/frenchlearning/api/AuthControllerIT.java`
- [X] T021 [P] [US1] Write failing JWT issuer, audience, expiry, algorithm, role mapping, missing-token, malformed-token, and `WWW-Authenticate` tests in `src/test/java/com/example/frenchlearning/security/JwtSecurityIT.java`

### Implementation for User Story 1

- [X] T022 [US1] Create user, role, user-role, normalized-email uniqueness, status, audit, and role seed schema in `src/main/resources/db/migration/V1__create_users_and_roles.sql`
- [X] T023 [P] [US1] Implement user and role persistence models in `src/main/java/com/example/frenchlearning/user/domain/User.java`, `UserStatus.java`, `Role.java`, `RoleName.java`, and `UserRole.java`
- [X] T024 [P] [US1] Implement user and role repositories with normalized-email and public-ID lookups in `src/main/java/com/example/frenchlearning/user/repository/UserRepository.java` and `RoleRepository.java`
- [X] T025 [P] [US1] Implement locale-independent email normalization and the documented password policy in `src/main/java/com/example/frenchlearning/auth/service/EmailNormalizer.java` and `PasswordPolicy.java`
- [ ] T026 [US1] Configure a delegating Argon2id password encoder, secure parameters, and encoding upgrades in `src/main/java/com/example/frenchlearning/security/PasswordConfiguration.java`
- [ ] T027 [US1] Implement authenticated principal and account loading with disabled/locked handling in `src/main/java/com/example/frenchlearning/security/AuthenticatedUser.java` and `src/main/java/com/example/frenchlearning/security/DatabaseUserDetailsService.java`
- [ ] T028 [US1] Implement asymmetric key loading, Nimbus encoder/decoder validation, 15-minute claims, audience checks, and role conversion in `src/main/java/com/example/frenchlearning/security/JwtConfiguration.java` and `src/main/java/com/example/frenchlearning/security/JwtTokenService.java`
- [X] T029 [P] [US1] Implement the shared problem serializer plus bearer authentication and access-denied handlers in `src/main/java/com/example/frenchlearning/security/SecurityProblemWriter.java`, `BearerAuthenticationEntryPoint.java`, and `BearerAccessDeniedHandler.java`
- [ ] T030 [US1] Configure stateless route authorization, resource-server JWT support, CORS allowlists, method security, and public auth/health routes in `src/main/java/com/example/frenchlearning/security/SecurityConfiguration.java`
- [ ] T031 [US1] Implement transactional registration with `USER` assignment and uniqueness-conflict translation in `src/main/java/com/example/frenchlearning/auth/service/RegistrationService.java`
- [ ] T032 [US1] Implement generic-timing login, authentication-manager delegation, token issuance, and bounded per-email/IP throttling in `src/main/java/com/example/frenchlearning/auth/service/AuthenticationService.java` and `LoginAttemptService.java`
- [ ] T033 [P] [US1] Implement registration/login DTOs and user/token response mapping in `src/main/java/com/example/frenchlearning/auth/dto/RegisterRequest.java`, `LoginRequest.java`, `UserResponse.java`, `AuthTokenResponse.java`, and `src/main/java/com/example/frenchlearning/auth/service/AuthMapper.java`
- [ ] T034 [US1] Implement `POST /api/v1/auth/register` and `POST /api/v1/auth/login` in `src/main/java/com/example/frenchlearning/auth/controller/AuthController.java`
- [ ] T035 [US1] Run and fix all US1 tests in `src/test/java/com/example/frenchlearning/unit/auth/RegistrationServiceTest.java`, `integration/user/UserRepositoryIT.java`, `api/AuthControllerIT.java`, and `security/JwtSecurityIT.java`

**Checkpoint**: User Story 1 is functional and independently testable as the authentication MVP.

---

## Phase 4: User Story 2 - Browse and Complete Lessons (Priority: P1)

**Goal**: An authenticated learner can browse ordered available lessons, retrieve lesson content, and idempotently record completion.

**Independent Test**: Seed one published and one retired lesson, retrieve only the available lesson, complete it twice, and verify exactly one completion and the expected learner-scoped completion state.

### Tests for User Story 2

- [ ] T036 [P] [US2] Write failing lesson catalog filtering, ordering, empty-catalog, and detail service tests in `src/test/java/com/example/frenchlearning/unit/lesson/LessonQueryServiceTest.java`
- [ ] T037 [P] [US2] Write failing lesson revision, active sequence, retirement, and projection repository tests in `src/test/java/com/example/frenchlearning/integration/lesson/LessonRepositoryIT.java`
- [ ] T038 [P] [US2] Write failing list/detail/completion contract, validation, authentication, and DTO-isolation tests in `src/test/java/com/example/frenchlearning/api/LessonControllerIT.java`
- [ ] T039 [P] [US2] Write failing duplicate and concurrent lesson-completion tests in `src/test/java/com/example/frenchlearning/integration/lesson/LessonCompletionIT.java`

### Implementation for User Story 2

- [ ] T040 [US2] Create lesson, lesson-revision, lesson-completion, revision ownership, active-order, snapshot, index, and constraint schema in `src/main/resources/db/migration/V2__create_lessons_and_completions.sql`
- [ ] T041 [P] [US2] Implement lesson, revision, completion, difficulty, and content-status models in `src/main/java/com/example/frenchlearning/lesson/domain/Lesson.java`, `LessonRevision.java`, `LessonCompletion.java`, `Difficulty.java`, and `ContentStatus.java`
- [ ] T042 [P] [US2] Implement active catalog/detail/completion persistence and projections in `src/main/java/com/example/frenchlearning/lesson/repository/LessonRepository.java`, `LessonRevisionRepository.java`, `LessonCompletionRepository.java`, and `LessonCatalogProjection.java`
- [ ] T043 [P] [US2] Implement learner lesson DTOs and filter validation in `src/main/java/com/example/frenchlearning/lesson/dto/LessonSummaryResponse.java`, `LessonDetailResponse.java`, and `LessonFilter.java`
- [ ] T044 [P] [US2] Implement entity/projection-to-DTO conversion without persistence fields in `src/main/java/com/example/frenchlearning/lesson/mapper/LessonMapper.java`
- [ ] T045 [US2] Implement authenticated active lesson pagination, topic/difficulty filtering, ordering, and not-found behavior in `src/main/java/com/example/frenchlearning/lesson/service/LessonQueryService.java`
- [ ] T046 [US2] Implement transactional idempotent completion using the authenticated subject and current revision snapshot in `src/main/java/com/example/frenchlearning/lesson/service/LessonCompletionService.java`
- [ ] T047 [US2] Implement `GET /api/v1/lessons`, `GET /api/v1/lessons/{lessonId}`, and `PUT /api/v1/lessons/{lessonId}/completion` in `src/main/java/com/example/frenchlearning/lesson/controller/LessonController.java`
- [ ] T048 [P] [US2] Add deterministic lesson and authenticated-user fixture builders in `src/test/java/com/example/frenchlearning/support/LessonTestData.java` and `AuthenticatedTestUser.java`
- [ ] T049 [US2] Run and fix all US2 tests in `src/test/java/com/example/frenchlearning/unit/lesson/LessonQueryServiceTest.java`, `integration/lesson/LessonRepositoryIT.java`, `integration/lesson/LessonCompletionIT.java`, and `api/LessonControllerIT.java`

**Checkpoint**: User Story 2 independently delivers ordered lesson consumption and one-time completion.

---

## Phase 5: User Story 3 - Take and Review Quizzes (Priority: P1)

**Goal**: An authenticated learner can retrieve safe quiz content, submit complete multiple-choice answers, receive an exact score, retry idempotently, and review only personal immutable attempts.

**Independent Test**: Seed a two-question published quiz, verify retrieval omits correctness, submit a fully correct attempt, retry the same idempotency key, and retrieve one owned attempt containing stable question-level outcomes.

### Tests for User Story 3

- [ ] T050 [P] [US3] Write failing equal-weight scoring, rounding, incomplete-answer, duplicate-question, and cross-question-choice unit tests in `src/test/java/com/example/frenchlearning/unit/quiz/QuizScoringServiceTest.java`
- [ ] T051 [P] [US3] Write failing quiz structure, publication validity, answer ownership, snapshot immutability, idempotency, and history index tests in `src/test/java/com/example/frenchlearning/integration/quiz/QuizPersistenceIT.java`
- [ ] T052 [P] [US3] Write failing quiz list/detail and submission API tests proving correct-answer indicators are absent before submission in `src/test/java/com/example/frenchlearning/api/QuizControllerIT.java`
- [ ] T053 [P] [US3] Write failing attempt cursor, retry, multi-attempt, ownership-hiding, and retired-content tests in `src/test/java/com/example/frenchlearning/api/QuizAttemptControllerIT.java`

### Implementation for User Story 3

- [ ] T054 [US3] Create quiz, quiz-revision, question, answer-choice, attempt, submitted-answer, snapshot, idempotency, ordering, ownership, score, and history index schema in `src/main/resources/db/migration/V3__create_quizzes_and_attempts.sql`
- [ ] T055 [P] [US3] Implement quiz content models in `src/main/java/com/example/frenchlearning/quiz/domain/Quiz.java`, `QuizRevision.java`, `Question.java`, and `AnswerChoice.java`
- [ ] T056 [P] [US3] Implement immutable result models in `src/main/java/com/example/frenchlearning/quiz/domain/QuizAttempt.java` and `SubmittedAnswer.java`
- [ ] T057 [P] [US3] Implement quiz, revision, question, choice, attempt, and submitted-answer repositories in `src/main/java/com/example/frenchlearning/quiz/repository/QuizRepository.java`, `QuizRevisionRepository.java`, `QuestionRepository.java`, `AnswerChoiceRepository.java`, `QuizAttemptRepository.java`, and `SubmittedAnswerRepository.java`
- [ ] T058 [P] [US3] Implement learner-safe quiz, submission, attempt, outcome, and cursor-page DTOs in `src/main/java/com/example/frenchlearning/quiz/dto/QuizSummaryResponse.java`, `QuizDetailResponse.java`, `QuizQuestionResponse.java`, `QuizChoiceResponse.java`, `QuizSubmissionRequest.java`, `SubmittedChoiceRequest.java`, `AttemptSummaryResponse.java`, `AttemptDetailResponse.java`, and `AnswerOutcomeResponse.java`
- [ ] T059 [P] [US3] Implement learner-safe quiz and immutable attempt mapping in `src/main/java/com/example/frenchlearning/quiz/mapper/LearnerQuizMapper.java` and `AttemptMapper.java`
- [ ] T060 [US3] Implement server-authoritative equal-weight score calculation and validation in `src/main/java/com/example/frenchlearning/quiz/service/QuizScoringService.java`
- [ ] T061 [US3] Implement available lesson quiz listing and correct-answer-safe quiz retrieval in `src/main/java/com/example/frenchlearning/quiz/service/QuizQueryService.java`
- [ ] T062 [US3] Implement one-transaction attempt validation, scoring, snapshots, persistence, and idempotency-conflict recovery in `src/main/java/com/example/frenchlearning/quiz/service/QuizSubmissionService.java`
- [ ] T063 [US3] Implement authenticated cursor history and ownership-hiding attempt detail retrieval in `src/main/java/com/example/frenchlearning/quiz/service/QuizAttemptQueryService.java`
- [ ] T064 [US3] Implement `GET /api/v1/lessons/{lessonId}/quizzes`, `GET /api/v1/quizzes/{quizId}`, and `POST /api/v1/quizzes/{quizId}/attempts` in `src/main/java/com/example/frenchlearning/quiz/controller/QuizController.java`
- [ ] T065 [US3] Implement `GET /api/v1/attempts` and `GET /api/v1/attempts/{attemptId}` in `src/main/java/com/example/frenchlearning/quiz/controller/QuizAttemptController.java`
- [ ] T066 [P] [US3] Add deterministic quiz, question, choice, submission, and attempt fixtures in `src/test/java/com/example/frenchlearning/support/QuizTestData.java`
- [ ] T067 [US3] Run and fix all US3 tests in `src/test/java/com/example/frenchlearning/unit/quiz/QuizScoringServiceTest.java`, `integration/quiz/QuizPersistenceIT.java`, `api/QuizControllerIT.java`, and `api/QuizAttemptControllerIT.java`

**Checkpoint**: User Story 3 independently delivers safe quiz-taking, exact scoring, and personal result review.

---

## Phase 6: User Story 4 - Review Personal Progress (Priority: P2)

**Goal**: An authenticated learner can retrieve an accurate lesson-completion and quiz-performance summary derived only from personal data.

**Independent Test**: Create known completions and attempts for two users, request progress as each user, and verify zero-state, percentage, latest/best score, recent attempts, and strict subject scoping.

### Tests for User Story 4

- [ ] T068 [P] [US4] Write failing zero-state, denominator, rounding, latest/best score, and recent-attempt service tests in `src/test/java/com/example/frenchlearning/unit/progress/ProgressServiceTest.java`
- [ ] T069 [P] [US4] Write failing aggregate-query and retired-lesson denominator tests against PostgreSQL in `src/test/java/com/example/frenchlearning/integration/progress/ProgressRepositoryIT.java`
- [ ] T070 [P] [US4] Write failing personal progress response and authentication tests in `src/test/java/com/example/frenchlearning/api/ProgressControllerIT.java`
- [ ] T071 [P] [US4] Write failing two-user ownership-isolation tests in `src/test/java/com/example/frenchlearning/security/ProgressOwnershipIT.java`

### Implementation for User Story 4

- [ ] T072 [P] [US4] Implement scoped aggregate queries and recent-attempt projections in `src/main/java/com/example/frenchlearning/progress/repository/ProgressRepository.java` and `ProgressProjection.java`
- [ ] T073 [P] [US4] Implement progress and recent-attempt response DTOs in `src/main/java/com/example/frenchlearning/progress/dto/ProgressResponse.java` and `RecentAttemptResponse.java`
- [ ] T074 [P] [US4] Implement deterministic percentage calculation with the zero-lessons rule in `src/main/java/com/example/frenchlearning/progress/service/ProgressCalculator.java`
- [ ] T075 [US4] Implement authenticated-subject-only progress orchestration and DTO mapping in `src/main/java/com/example/frenchlearning/progress/service/ProgressService.java`
- [ ] T076 [US4] Implement `GET /api/v1/progress/me` in `src/main/java/com/example/frenchlearning/progress/controller/ProgressController.java`
- [ ] T077 [US4] Run and fix all US4 tests in `src/test/java/com/example/frenchlearning/unit/progress/ProgressServiceTest.java`, `integration/progress/ProgressRepositoryIT.java`, `api/ProgressControllerIT.java`, and `security/ProgressOwnershipIT.java`

**Checkpoint**: User Story 4 independently returns exact, private progress for active and zero-activity learners.

---

## Phase 7: User Story 5 - Manage Learning Content (Priority: P2)

**Goal**: An administrator can create, retrieve, revise, publish, order, and retire lessons and quizzes while learners are denied and historical learner results remain unchanged.

**Independent Test**: Authenticate as `ADMIN`, create and revise a lesson and quiz, verify learner denial, retire both, and confirm catalogs block new activity while a previous attempt retains its original snapshots and score.

### Tests for User Story 5

- [ ] T078 [P] [US5] Write failing lesson draft, publish, replacement revision, ordering-conflict, optimistic-lock, and retirement service tests in `src/test/java/com/example/frenchlearning/unit/lesson/LessonAdminServiceTest.java`
- [ ] T079 [P] [US5] Write failing quiz structure, single-correct-choice, replacement revision, ordering, optimistic-lock, and retirement service tests in `src/test/java/com/example/frenchlearning/unit/quiz/QuizAdminServiceTest.java`
- [ ] T080 [P] [US5] Write failing published-content and submitted-history immutability trigger tests in `src/test/java/com/example/frenchlearning/integration/admin/ContentImmutabilityIT.java`
- [ ] T081 [P] [US5] Write failing admin CRUD contracts, `If-Match`, validation, `USER` denial, and `ADMIN` success tests in `src/test/java/com/example/frenchlearning/api/AdminContentControllerIT.java`
- [ ] T082 [P] [US5] Write failing local-profile-only, idempotent, non-logging admin bootstrap tests in `src/test/java/com/example/frenchlearning/integration/admin/LocalAdminBootstrapIT.java`

### Implementation for User Story 5

- [ ] T083 [US5] Add database enforcement for published revision and submitted history immutability plus active ordering in `src/main/resources/db/migration/V4__enforce_content_immutability.sql`
- [ ] T084 [P] [US5] Implement lesson administration request/response DTOs in `src/main/java/com/example/frenchlearning/lesson/dto/LessonUpsertRequest.java` and `AdminLessonResponse.java`
- [ ] T085 [P] [US5] Implement quiz administration request/response DTOs with nested question/choice validation in `src/main/java/com/example/frenchlearning/quiz/dto/QuizUpsertRequest.java`, `AdminQuestionInput.java`, `AdminChoiceInput.java`, and `AdminQuizResponse.java`
- [ ] T086 [P] [US5] Implement administrative lesson mapping including lifecycle and version fields in `src/main/java/com/example/frenchlearning/lesson/mapper/AdminLessonMapper.java`
- [ ] T087 [P] [US5] Implement administrative quiz mapping including correct-answer and lifecycle fields in `src/main/java/com/example/frenchlearning/quiz/mapper/AdminQuizMapper.java`
- [ ] T088 [US5] Implement shared draft validation and atomic publish/supersede state transitions in `src/main/java/com/example/frenchlearning/common/persistence/RevisionPublicationService.java`
- [ ] T089 [US5] Implement `ADMIN`-guarded lesson listing, detail, create, replacement revision, publish, ordering, and retirement in `src/main/java/com/example/frenchlearning/lesson/service/LessonAdminService.java`
- [ ] T090 [US5] Implement `ADMIN`-guarded quiz detail, create, structural validation, replacement revision, publish, ordering, and retirement in `src/main/java/com/example/frenchlearning/quiz/service/QuizAdminService.java`
- [ ] T091 [US5] Implement administrative lesson list/detail/create/update/delete routes in `src/main/java/com/example/frenchlearning/lesson/controller/AdminLessonController.java`
- [ ] T092 [US5] Implement administrative quiz detail/create/update/delete routes in `src/main/java/com/example/frenchlearning/quiz/controller/AdminQuizController.java`
- [ ] T093 [P] [US5] Implement quoted numeric `If-Match` parsing and stale-version conflict handling in `src/main/java/com/example/frenchlearning/common/api/IfMatchVersionResolver.java`
- [ ] T094 [US5] Implement disabled-by-default, local-profile-only, idempotent administrator bootstrap without secret logging in `src/main/java/com/example/frenchlearning/configuration/LocalAdminBootstrap.java`
- [ ] T095 [P] [US5] Document controlled production administrator provisioning and credential rotation in `docs/admin-provisioning.md`
- [ ] T096 [US5] Run and fix all US5 tests in `src/test/java/com/example/frenchlearning/unit/lesson/LessonAdminServiceTest.java`, `unit/quiz/QuizAdminServiceTest.java`, `integration/admin/ContentImmutabilityIT.java`, `integration/admin/LocalAdminBootstrapIT.java`, and `api/AdminContentControllerIT.java`

**Checkpoint**: User Story 5 independently delivers role-protected, revision-safe content administration.

---

## Phase 8: User Story 6 - Understand and Integrate with Public Interfaces (Priority: P3)

**Goal**: Consumers can discover and exercise every public operation, model, authentication rule, response, and error through an accurate interactive OpenAPI reference.

**Independent Test**: Start the service, open Swagger UI, authorize with a learner or admin JWT, execute each relevant workflow, and verify the served document conforms to the committed contract.

### Tests for User Story 6

- [ ] T097 [P] [US6] Write a failing committed OpenAPI parse, required-operation, security, response, and schema test in `src/test/java/com/example/frenchlearning/api/OpenApiContractTest.java`
- [ ] T098 [P] [US6] Write a failing served `/v3/api-docs` and Swagger UI bearer-security integration test in `src/test/java/com/example/frenchlearning/api/SwaggerDocumentationIT.java`

### Implementation for User Story 6

- [ ] T099 [US6] Configure OpenAPI metadata, grouped APIs, JWT bearer scheme, response conventions, and Swagger UI authorization in `src/main/java/com/example/frenchlearning/configuration/OpenApiConfiguration.java` and `src/main/resources/application.yml`
- [ ] T100 [P] [US6] Add operation, validation, authorization, and response documentation to `src/main/java/com/example/frenchlearning/auth/controller/AuthController.java`, `lesson/controller/LessonController.java`, and `progress/controller/ProgressController.java`
- [ ] T101 [P] [US6] Add learner quiz and attempt operation documentation to `src/main/java/com/example/frenchlearning/quiz/controller/QuizController.java` and `QuizAttemptController.java`
- [ ] T102 [P] [US6] Add administrative lifecycle, `If-Match`, and role requirement documentation to `src/main/java/com/example/frenchlearning/lesson/controller/AdminLessonController.java` and `src/main/java/com/example/frenchlearning/quiz/controller/AdminQuizController.java`
- [ ] T103 [P] [US6] Add reusable documented problem and success-envelope examples in `src/main/java/com/example/frenchlearning/configuration/OpenApiExamples.java`
- [ ] T104 [US6] Configure build-time Redocly validation and served-versus-committed contract comparison for `specs/001-french-learning-backend/contracts/openapi.yaml` in `pom.xml`
- [ ] T105 [US6] Run and fix US6 contract tests in `src/test/java/com/example/frenchlearning/api/OpenApiContractTest.java` and `SwaggerDocumentationIT.java`, then manually exercise JWT authorization at `/swagger-ui.html`

**Checkpoint**: User Story 6 independently provides an accurate, interactive, authenticated API reference.

---

## Phase 9: Polish & Cross-Cutting Concerns

**Purpose**: Complete production packaging, hardening, performance evidence, architecture checks, and handoff documentation across all stories.

- [ ] T106 [P] Create a non-root multi-stage production image with Java 21 runtime, health check, and no embedded secrets in `Dockerfile` and `.dockerignore`
- [ ] T107 [P] Configure production-safe shutdown, proxy handling, datasource pooling, migration validation, restricted management exposure, and log levels in `src/main/resources/application-prod.yml`
- [ ] T108 [P] Complete setup, environment, migration, run, test, Swagger, troubleshooting, and operational instructions in `README.md`
- [ ] T109 [P] Add cross-cutting validation, problem-shape, sensitive-data redaction, unsupported-method, unsupported-media-type, malformed JSON, and unexpected-error tests in `src/test/java/com/example/frenchlearning/api/GlobalExceptionHandlerIT.java`
- [ ] T110 [P] Add architecture tests enforcing controller/service/repository boundaries and preventing entity types in controller signatures in `src/test/java/com/example/frenchlearning/architecture/LayeringTest.java`
- [ ] T111 [P] Add a representative 500-active-learner browse/quiz/progress performance scenario and result thresholds in `src/test/java/com/example/frenchlearning/performance/LearningWorkflowPerformanceIT.java`
- [ ] T112 Review query plans for normalized email, lesson catalog, completion, attempt history, and progress aggregates, then add only evidence-backed indexes in `src/main/resources/db/migration/V5__optimize_required_queries.sql`
- [ ] T113 [P] Add JWT key failure, algorithm confusion, audience/issuer mismatch, CORS, role-staleness documentation, and administrator boundary tests in `src/test/java/com/example/frenchlearning/security/SecurityHardeningIT.java`
- [ ] T114 [P] Add safe structured audit events for registration, login outcome, content publication/retirement, and quiz submission in `src/main/java/com/example/frenchlearning/configuration/SecurityAuditLogger.java`
- [ ] T115 Execute every scenario in `specs/001-french-learning-backend/quickstart.md` against a clean local database and update mismatched commands or expected outcomes in that file
- [ ] T116 Run `.\mvnw.cmd clean verify` from `pom.xml`, fix all unit/integration/API/security/architecture checks, and confirm Flyway can migrate an empty PostgreSQL database
- [ ] T117 Validate `specs/001-french-learning-backend/contracts/openapi.yaml` with Redocly, verify no undocumented public routes, and confirm the repository contains no credentials, private keys, generated secrets, or exposed persistence entities

**Checkpoint**: The service is runnable, documented, hardened, migration-safe, contract-complete, and verified against the specification.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies.
- **Foundational (Phase 2)**: Depends on Setup and blocks all user stories.
- **US1 (Phase 3)**: Depends on Foundational and establishes identity/security.
- **US2 (Phase 4)**: Depends on US1 for authenticated ownership and user foreign keys.
- **US3 (Phase 5)**: Depends on US1 and the lesson identity from US2.
- **US4 (Phase 6)**: Depends on completion data from US2 and attempt data from US3.
- **US5 (Phase 7)**: Depends on US1 authorization plus lesson/quiz models from US2 and US3.
- **US6 (Phase 8)**: Depends on implemented routes from US1–US5.
- **Polish (Phase 9)**: Depends on all selected user stories.

### User Story Dependency Graph

```text
Setup → Foundation → US1 → US2 → US3
                         ├───────→ US4
                         └───────→ US5
US1 + US2 + US3 + US4 + US5 → US6 → Polish
```

US4 and US5 can run in parallel after US2 and US3 are complete.

### Within Each User Story

1. Write the story's tests and verify they fail for the missing behavior.
2. Apply schema migrations before persistence models that require them.
3. Implement models and repositories before services.
4. Implement services before controllers.
5. Run the complete story test set before crossing its checkpoint.

## Parallel Opportunities

- Setup tasks T003–T005 and T007 target separate files.
- Foundational tasks T009–T013 and T015–T016 target separate concerns after T008.
- Tests marked `[P]` within a story can be authored together before implementation.
- DTOs, mappers, and non-dependent domain classes marked `[P]` can be implemented concurrently.
- US4 progress work and US5 administration work can proceed concurrently after US3.
- Production packaging, documentation, exception tests, architecture tests, performance tests, and security tests in Phase 9 have separate targets.

## Parallel Execution Examples

### User Story 1

```text
Parallel test batch: T018 RegistrationServiceTest, T019 UserRepositoryIT, T020 AuthControllerIT, T021 JwtSecurityIT
Parallel implementation batch after T022: T023 domain models, T024 repositories, T025 normalization/policy, T029 security problem handlers, T033 DTOs/mapping
```

### User Story 2

```text
Parallel test batch: T036 LessonQueryServiceTest, T037 LessonRepositoryIT, T038 LessonControllerIT, T039 LessonCompletionIT
Parallel implementation batch after T040: T041 domain models, T042 repositories, T043 DTOs, T044 mapper, T048 fixtures
```

### User Story 3

```text
Parallel test batch: T050 QuizScoringServiceTest, T051 QuizPersistenceIT, T052 QuizControllerIT, T053 QuizAttemptControllerIT
Parallel implementation batch after T054: T055 quiz models, T056 result models, T057 repositories, T058 DTOs, T059 mappers, T066 fixtures
```

### User Story 4

```text
Parallel test batch: T068 ProgressServiceTest, T069 ProgressRepositoryIT, T070 ProgressControllerIT, T071 ProgressOwnershipIT
Parallel implementation batch: T072 aggregate repository, T073 DTOs, T074 calculator
```

### User Story 5

```text
Parallel test batch: T078 LessonAdminServiceTest, T079 QuizAdminServiceTest, T080 ContentImmutabilityIT, T081 AdminContentControllerIT, T082 LocalAdminBootstrapIT
Parallel implementation batch after T083: T084 lesson DTOs, T085 quiz DTOs, T086 lesson mapper, T087 quiz mapper, T093 If-Match resolver, T095 runbook
```

### User Story 6

```text
Parallel test batch: T097 OpenApiContractTest, T098 SwaggerDocumentationIT
Parallel documentation batch after T099: T100 auth/lesson/progress controllers, T101 learner quiz controllers, T102 admin controllers, T103 examples
```

## Implementation Strategy

### MVP First

1. Complete Setup and Foundational phases.
2. Complete US1 and validate secure registration/login independently.
3. Stop for the authentication MVP checkpoint.
4. Add US2 for the first learner-value MVP: authenticated lesson browsing and completion.

### Incremental Delivery

1. **Authentication MVP**: Setup + Foundation + US1.
2. **Learning MVP**: Add US2 and validate lesson consumption.
3. **Assessment increment**: Add US3 and validate scoring/history.
4. **Learner insight increment**: Add US4.
5. **Content operations increment**: Add US5.
6. **Integration readiness**: Add US6.
7. **Production readiness**: Complete Polish.

### Parallel Team Strategy

1. Complete Setup and Foundation together.
2. Deliver US1, US2, and US3 in dependency order while parallelizing tests, DTOs, repositories, and mappers within each phase.
3. Split after US3:
   - Developer A: US4 progress.
   - Developer B: US5 administration.
   - Developer C: prepare US6 contract tests against completed routes.
4. Rejoin for US6 and cross-cutting production verification.

## Notes

- Every `[P]` task changes separate files and can run concurrently after its prerequisites.
- Learner ownership always comes from the authenticated JWT subject, never a request-supplied user ID.
- Published content and submitted history remain immutable; administrative delete operations retire referenced content.
- The committed OpenAPI file is the contract source for endpoint status codes, bodies, security, headers, and pagination.
- Do not expose JPA entities from controllers or serialize password hashes, correct answers before submission, keys, tokens, or internal exception details.
- Complete and verify each story checkpoint before relying on it from a later phase.
