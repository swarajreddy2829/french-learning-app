# Data Model: French Learning Backend

**Date**: 2026-08-16  
**Source**: [Feature specification](./spec.md) and [research decisions](./research.md)

## Modeling Conventions

- Internal primary keys are generated `BIGINT` values.
- Externally visible users and learning resources also have immutable unique UUID `public_id` values.
- All timestamps are UTC instants stored as PostgreSQL `timestamptz`.
- Mutable rows include a numeric optimistic-lock `version`.
- Flyway owns all tables, constraints, indexes, and reference data; the object mapper validates but does not create the schema.
- Public DTOs never serialize persistence models directly.
- Published content and submitted learning history are immutable. Content is retired rather than destructively deleted when history references it.

## Relationship Overview

```text
User ──< UserRole >── Role
User ──< LessonCompletion >── Lesson ──< LessonRevision
Lesson ──< Quiz ──< QuizRevision ──< Question ──< AnswerChoice
User ──< QuizAttempt >── QuizRevision
QuizAttempt ──< SubmittedAnswer >── Question / AnswerChoice

Lesson.currentRevision ──> LessonRevision
Quiz.currentRevision ──> QuizRevision
LessonCompletion.lessonRevision ──> LessonRevision
```

## Entities

### User

Represents a registered learner or administrator.

| Field | Type | Rules |
|---|---|---|
| `id` | BIGINT | Primary key, generated |
| `public_id` | UUID | Required, immutable, unique, exposed externally |
| `email` | VARCHAR(320) | Required; trimmed display form |
| `normalized_email` | VARCHAR(320) | Required, immutable normalization policy, unique |
| `password_hash` | VARCHAR(255) | Required; delegating-encoder identifier plus protected hash; never exposed |
| `status` | VARCHAR(20) | `ACTIVE`, `DISABLED`, or `LOCKED` |
| `created_at` | TIMESTAMPTZ | Required |
| `updated_at` | TIMESTAMPTZ | Required |
| `version` | BIGINT | Required optimistic-lock value |

Validation and constraints:

- `UNIQUE(normalized_email)` is the authoritative duplicate-registration guard.
- Public registration always creates an `ACTIVE` user with only the `USER` role.
- Password hashes may change during a successful encoding upgrade; plain passwords are never persisted.
- Email normalization trims surrounding whitespace and applies locale-independent lowercase normalization.

### Role

Reference data defining an authorization role.

| Field | Type | Rules |
|---|---|---|
| `id` | SMALLINT | Primary key |
| `name` | VARCHAR(20) | Required, unique; initial values `USER`, `ADMIN` |

Roles are seeded by migration and are not managed through the public API.

### UserRole

Associates users with roles.

| Field | Type | Rules |
|---|---|---|
| `user_id` | BIGINT | FK to `user.id`, part of primary key |
| `role_id` | SMALLINT | FK to `role.id`, part of primary key |
| `granted_at` | TIMESTAMPTZ | Required |

Constraints:

- Primary key `(user_id, role_id)` prevents duplicate grants.
- Administrative grants occur only through a controlled operational process.

### Lesson

Stable identity and lifecycle for a lesson. Learners consume its current published revision.

| Field | Type | Rules |
|---|---|---|
| `id` | BIGINT | Primary key, generated |
| `public_id` | UUID | Required, immutable, unique, exposed externally |
| `current_revision_id` | BIGINT | Nullable FK to `lesson_revision.id`; set when first published |
| `retired_at` | TIMESTAMPTZ | Null while available; set on administrative deletion |
| `created_at` | TIMESTAMPTZ | Required |
| `updated_at` | TIMESTAMPTZ | Required |
| `version` | BIGINT | Required optimistic-lock value |

Constraints:

- `current_revision_id`, when set, must reference a revision belonging to the same lesson. This is enforced through a composite unique key on revisions and a composite FK.
- A lesson is learner-visible only when `retired_at IS NULL` and its current revision is `PUBLISHED`.
- Retirement does not remove revisions, completions, quizzes, or attempts.

### LessonRevision

Immutable published representation of lesson content; draft revisions may be edited until publication.

| Field | Type | Rules |
|---|---|---|
| `id` | BIGINT | Primary key, generated |
| `lesson_id` | BIGINT | Required FK to `lesson.id` |
| `revision_no` | INTEGER | Required, starts at 1 and increases per lesson |
| `status` | VARCHAR(20) | `DRAFT`, `PUBLISHED`, or `SUPERSEDED` |
| `title` | VARCHAR(200) | Required, trimmed |
| `description` | VARCHAR(1000) | Required, trimmed |
| `content` | TEXT | Required, non-blank |
| `difficulty` | VARCHAR(20) | `BEGINNER`, `INTERMEDIATE`, or `ADVANCED` |
| `topic` | VARCHAR(100) | Required, trimmed |
| `sequence_no` | INTEGER | Required, greater than zero |
| `created_by` | BIGINT | Required FK to administrator `user.id` |
| `created_at` | TIMESTAMPTZ | Required |
| `published_at` | TIMESTAMPTZ | Required only when published/superseded |
| `version` | BIGINT | Optimistic lock for drafts |

Constraints:

- `UNIQUE(lesson_id, revision_no)`.
- Only one non-retired lesson's current published revision may occupy a given `sequence_no`; publication performs this check transactionally and a maintained active-order projection/constraint enforces it.
- Published and superseded semantic fields cannot be updated or deleted.
- Publishing a draft marks the previous current revision `SUPERSEDED` and updates `lesson.current_revision_id` atomically.

### Quiz

Stable quiz identity associated with one lesson.

| Field | Type | Rules |
|---|---|---|
| `id` | BIGINT | Primary key, generated |
| `public_id` | UUID | Required, immutable, unique, exposed externally |
| `lesson_id` | BIGINT | Required FK to `lesson.id` |
| `current_revision_id` | BIGINT | Nullable FK to `quiz_revision.id` |
| `sequence_no` | INTEGER | Required, greater than zero within lesson |
| `retired_at` | TIMESTAMPTZ | Null while available |
| `created_at` | TIMESTAMPTZ | Required |
| `updated_at` | TIMESTAMPTZ | Required |
| `version` | BIGINT | Required optimistic-lock value |

Constraints:

- `UNIQUE(lesson_id, sequence_no)` for non-retired quizzes.
- Current revision must belong to the same quiz.
- A quiz is learner-visible only when the quiz and parent lesson are not retired and its current revision is published.

### QuizRevision

Immutable published quiz definition.

| Field | Type | Rules |
|---|---|---|
| `id` | BIGINT | Primary key, generated |
| `quiz_id` | BIGINT | Required FK to `quiz.id` |
| `revision_no` | INTEGER | Required, starts at 1 |
| `status` | VARCHAR(20) | `DRAFT`, `PUBLISHED`, or `SUPERSEDED` |
| `title` | VARCHAR(200) | Required |
| `instructions` | VARCHAR(1000) | Optional |
| `created_by` | BIGINT | Required FK to administrator `user.id` |
| `created_at` | TIMESTAMPTZ | Required |
| `published_at` | TIMESTAMPTZ | Required only when published/superseded |
| `version` | BIGINT | Optimistic lock for drafts |

Constraints:

- `UNIQUE(quiz_id, revision_no)`.
- Publication requires at least one question, at least two choices per question, and exactly one correct choice per question.
- Published and superseded revisions, questions, and choices cannot be changed or deleted.

### Question

An ordered multiple-choice question in one quiz revision.

| Field | Type | Rules |
|---|---|---|
| `id` | BIGINT | Primary key, generated |
| `quiz_revision_id` | BIGINT | Required FK to `quiz_revision.id` |
| `position` | INTEGER | Required, greater than zero |
| `prompt` | TEXT | Required, non-blank |
| `points` | INTEGER | Required; fixed at 1 in v1 |

Constraints:

- `UNIQUE(quiz_revision_id, position)`.
- `CHECK(points = 1)` in v1.

### AnswerChoice

A selectable choice belonging to one question.

| Field | Type | Rules |
|---|---|---|
| `id` | BIGINT | Primary key, generated |
| `question_id` | BIGINT | Required FK to `question.id` |
| `position` | INTEGER | Required, greater than zero |
| `label` | TEXT | Required, non-blank |
| `correct` | BOOLEAN | Required |

Constraints:

- `UNIQUE(question_id, position)`.
- `UNIQUE(id, question_id)` supports composite answer-ownership references.
- Publication validation plus a database-maintained invariant ensures exactly one correct choice per published question.

### QuizAttempt

Immutable record of a submitted quiz attempt.

| Field | Type | Rules |
|---|---|---|
| `id` | BIGINT | Primary key, generated |
| `public_id` | UUID | Required, immutable, unique, exposed externally |
| `user_id` | BIGINT | Required FK to `user.id` |
| `quiz_id` | BIGINT | Required FK to stable `quiz.id` |
| `quiz_revision_id` | BIGINT | Required FK to exact `quiz_revision.id` |
| `submission_key` | UUID | Required client idempotency key |
| `quiz_title_snapshot` | VARCHAR(200) | Required |
| `earned_points` | INTEGER | Required, zero or greater |
| `possible_points` | INTEGER | Required, greater than zero |
| `score_percent` | NUMERIC(5,2) | Required, from 0.00 through 100.00 |
| `submitted_at` | TIMESTAMPTZ | Required |

Constraints:

- `UNIQUE(user_id, submission_key)` makes a retried POST return the original result rather than create a duplicate.
- Attempt ownership and the referenced revision are fixed at creation.
- Earned points cannot exceed possible points.
- Attempts cannot be updated or deleted after insertion.
- One transaction validates all submitted choices, calculates the score, inserts the attempt and answers, and commits or rolls back as a unit.

### SubmittedAnswer

Immutable question-level outcome for an attempt.

| Field | Type | Rules |
|---|---|---|
| `id` | BIGINT | Primary key, generated |
| `attempt_id` | BIGINT | Required FK to `quiz_attempt.id` |
| `question_id` | BIGINT | Required FK to `question.id` |
| `selected_choice_id` | BIGINT | Required |
| `question_position` | INTEGER | Required snapshot |
| `question_prompt_snapshot` | TEXT | Required snapshot |
| `selected_choice_snapshot` | TEXT | Required snapshot |
| `correct_choice_snapshot` | TEXT | Required snapshot, returned only after submission |
| `correct` | BOOLEAN | Required outcome |
| `awarded_points` | INTEGER | Required, zero or one |

Constraints:

- `UNIQUE(attempt_id, question_id)` ensures one answer per attempted question.
- Composite FK `(selected_choice_id, question_id)` references `answer_choice(id, question_id)`, preventing cross-question choices.
- Submitted-answer count must equal the referenced revision's question count before commit.
- Rows cannot be updated or deleted.

### LessonCompletion

Records the first time a learner completes a stable lesson.

| Field | Type | Rules |
|---|---|---|
| `id` | BIGINT | Primary key, generated |
| `user_id` | BIGINT | Required FK to `user.id` |
| `lesson_id` | BIGINT | Required FK to `lesson.id` |
| `lesson_revision_id` | BIGINT | Required FK to revision viewed at completion |
| `lesson_title_snapshot` | VARCHAR(200) | Required |
| `completed_at` | TIMESTAMPTZ | Required |

Constraints:

- `UNIQUE(user_id, lesson_id)` makes completion idempotent.
- A completion can be created only for an available lesson and a revision belonging to that lesson.
- Completion history is retained when a lesson is retired.

### ProgressSummary

A read model calculated for the authenticated user; it is not a table.

| Field | Derivation |
|---|---|
| `available_lessons` | Count of non-retired lessons with a published current revision |
| `completed_available_lessons` | Distinct completions whose stable lesson remains available |
| `lesson_completion_percent` | Completed available lessons / available lessons × 100; `0.00` when none are available |
| `quiz_attempt_count` | Count of the user's submitted attempts |
| `latest_quiz_score` | Score on the most recently submitted attempt, or null |
| `best_quiz_score` | Maximum score across submitted attempts, or null |
| `recent_attempts` | Bounded newest-first projection |

The read model always scopes queries to the authenticated user's internal ID.

## State Transitions

### User

```text
ACTIVE ──administrative disable──> DISABLED
ACTIVE ──security lock────────────> LOCKED
DISABLED/LOCKED ──authorized restore──> ACTIVE
```

Only `ACTIVE` users may log in. Existing stateless tokens remain bounded by their short expiry; immediate token revocation is outside v1.

### Lesson and Quiz Content

```text
DRAFT ──validate and publish──> PUBLISHED
PUBLISHED ──publish replacement──> SUPERSEDED
Stable identity available ──delete command──> RETIRED
```

- Drafts are mutable under optimistic locking.
- Published and superseded revisions are immutable.
- Retirement blocks new learner reads, completions, and attempts but preserves history.

### Quiz Attempt

The public submission operation creates a complete `SUBMITTED` attempt atomically. No persistent draft-attempt state exists in v1. A repeated request with the same submission key returns the existing attempt; a new key creates a separate allowed attempt.

## Transaction Boundaries

### Register user

1. Normalize email and validate password.
2. Perform password hashing.
3. Insert user and `USER` role grant in one transaction.
4. Translate normalized-email uniqueness conflicts to `EMAIL_ALREADY_REGISTERED`.

### Publish lesson or quiz revision

1. Lock stable identity or verify its optimistic version.
2. Validate complete draft and ordering rules.
3. Mark previous published revision superseded.
4. Mark draft published and point stable identity to it.
5. Commit all changes atomically.

### Submit quiz

1. Resolve authenticated user and current available quiz revision.
2. Check idempotency key and return an existing owned result if present.
3. Validate exactly one supplied choice for every question and choice ownership.
4. Calculate score from server-side correctness data.
5. Insert attempt and all answer snapshots in one transaction.
6. Translate concurrent idempotency-key conflicts by loading and returning the winning attempt.

### Complete lesson

1. Resolve authenticated user and current available lesson revision.
2. Insert completion.
3. On `(user_id, lesson_id)` conflict, treat the operation as successful and return no duplicate.

## Index Plan

- Unique B-tree on `user(normalized_email)`.
- Unique B-tree on every `public_id`.
- `user_role(role_id, user_id)` in addition to its user-first primary key.
- Unique `lesson_revision(lesson_id, revision_no)`.
- Active lesson catalog index supporting current sequence, topic, and difficulty.
- Unique active sequence enforcement for learner-visible lessons.
- `quiz(lesson_id, sequence_no)` with an active-content predicate.
- Unique `quiz_revision(quiz_id, revision_no)`.
- Unique `question(quiz_revision_id, position)`.
- Unique `answer_choice(question_id, position)`.
- `quiz_attempt(user_id, submitted_at DESC, id DESC)` for cursor history.
- `quiz_attempt(quiz_revision_id, submitted_at DESC)` for administration and diagnostics.
- Unique `quiz_attempt(user_id, submission_key)`.
- Unique `submitted_answer(attempt_id, question_id)`.
- `lesson_completion(user_id, completed_at DESC, lesson_id)`.
- Index all foreign-key columns used for joins or retirement checks; PostgreSQL does not add them automatically.

Specialized indexes are added only after representative `EXPLAIN (ANALYZE, BUFFERS)` evidence. Partitioning is not planned at the initial scale.

## Deletion and Retention Rules

- Users with learning history are disabled, not physically removed, within v1.
- Stable lessons and quizzes are retired when referenced by history.
- Unpublished, unreferenced drafts may be physically deleted by administrators.
- Published revisions, attempts, submitted answers, and completions use restrictive foreign keys and immutability enforcement.
- No relationship cascades from content to learner history.
