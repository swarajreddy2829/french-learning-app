# Quickstart and Validation: French Learning Backend

This guide defines the runnable setup and end-to-end checks the implementation must support. It validates the public contract in [contracts/openapi.yaml](./contracts/openapi.yaml) and the rules in [data-model.md](./data-model.md).

## Prerequisites

- Java 21
- Docker with Compose
- PowerShell 7+ (examples below)
- OpenSSL for generating local JWT keys
- Ports `8080` and `5432` available

The completed repository must include Maven Wrapper, `compose.yaml`, safe `.env.example` values, and no committed credentials or signing keys.

## 1. Configure local secrets

Generate local-only RSA signing keys:

```powershell
New-Item -ItemType Directory -Force -Path .local\keys | Out-Null
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 -out .local\keys\jwt-private.pem
openssl rsa -pubout -in .local\keys\jwt-private.pem -out .local\keys\jwt-public.pem
```

Set environment variables for the current shell:

```powershell
$env:DB_URL = "jdbc:postgresql://localhost:5432/french_learning"
$env:DB_USERNAME = "french_app"
$env:DB_PASSWORD = "local-change-me"
$env:JWT_PRIVATE_KEY_PATH = "$PWD\.local\keys\jwt-private.pem"
$env:JWT_PUBLIC_KEY_PATH = "$PWD\.local\keys\jwt-public.pem"
$env:JWT_ISSUER = "http://localhost:8080"
$env:JWT_AUDIENCE = "french-learning-api"
```

Local administrator bootstrap is permitted only in the `local` profile and is disabled by default elsewhere:

```powershell
$env:APP_BOOTSTRAP_ADMIN_ENABLED = "true"
$env:APP_BOOTSTRAP_ADMIN_EMAIL = "admin@example.test"
$env:APP_BOOTSTRAP_ADMIN_PASSWORD = "Admin-local-only-123!"
```

The implementation must create or grant this local administrator idempotently without logging the password. Production administrator provisioning uses a controlled operational runbook, not these bootstrap variables.

## 2. Start PostgreSQL

```powershell
docker compose up -d postgres
docker compose ps
```

Expected outcome:

- PostgreSQL reports healthy.
- The database and application user exist.
- No application schema is required before startup; Flyway creates it.

## 3. Build and verify

```powershell
.\mvnw.cmd clean verify
```

Expected outcome:

- Unit tests pass.
- PostgreSQL integration tests run through Testcontainers.
- Flyway migrations apply from an empty database and pass validation.
- API, authentication, authorization, validation, and error-contract tests pass.
- The build fails if generated or served OpenAPI behavior diverges from the committed contract.

## 4. Run the service

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local"
```

In a second PowerShell terminal:

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health/liveness
Invoke-RestMethod http://localhost:8080/actuator/health/readiness
```

Expected outcome: both return `status: UP`. Readiness requires a usable database and completed migrations. Detailed dependency or configuration data is not returned publicly.

The interactive API reference must be available at:

```text
http://localhost:8080/swagger-ui.html
```

## 5. Register and authenticate a learner

```powershell
$base = "http://localhost:8080/api/v1"

$registration = @{
  email = "learner@example.test"
  password = "Learner-local-123!"
} | ConvertTo-Json

$user = Invoke-RestMethod `
  -Method Post `
  -Uri "$base/auth/register" `
  -ContentType "application/json" `
  -Body $registration

$login = Invoke-RestMethod `
  -Method Post `
  -Uri "$base/auth/login" `
  -ContentType "application/json" `
  -Body $registration

$learnerHeaders = @{
  Authorization = "Bearer $($login.data.accessToken)"
}
```

Expected outcome:

- Registration returns `201` with role `USER` and no password field.
- Login returns `200`, token type `Bearer`, and a positive expiry.
- Re-registering ` LEARNER@EXAMPLE.TEST ` returns `409` with code `EMAIL_ALREADY_REGISTERED`.
- Invalid credentials return a generic `401` without revealing whether the account exists.

## 6. Authenticate the local administrator

```powershell
$adminLoginBody = @{
  email = $env:APP_BOOTSTRAP_ADMIN_EMAIL
  password = $env:APP_BOOTSTRAP_ADMIN_PASSWORD
} | ConvertTo-Json

$adminLogin = Invoke-RestMethod `
  -Method Post `
  -Uri "$base/auth/login" `
  -ContentType "application/json" `
  -Body $adminLoginBody

$adminHeaders = @{
  Authorization = "Bearer $($adminLogin.data.accessToken)"
}
```

Expected outcome: the token represents an account with `ADMIN`.

## 7. Create a lesson as administrator

```powershell
$lessonBody = @{
  title = "French Greetings"
  description = "Learn common greetings and introductions."
  content = "Bonjour means hello. Au revoir means goodbye."
  difficulty = "BEGINNER"
  topic = "Greetings"
  sequence = 1
  publish = $true
} | ConvertTo-Json

$lesson = Invoke-RestMethod `
  -Method Post `
  -Uri "$base/admin/lessons" `
  -Headers $adminHeaders `
  -ContentType "application/json" `
  -Body $lessonBody

$lessonId = $lesson.data.id
```

Expected outcome: creation returns `201`; the lesson is published at sequence 1.

Verify role enforcement:

```powershell
try {
  Invoke-RestMethod `
    -Method Post `
    -Uri "$base/admin/lessons" `
    -Headers $learnerHeaders `
    -ContentType "application/json" `
    -Body $lessonBody
  throw "Expected learner administration request to fail"
} catch {
  if ($_.Exception.Response.StatusCode.value__ -ne 403) { throw }
}
```

Expected outcome: a learner receives `403` in the standard problem format.

## 8. Create a quiz as administrator

```powershell
$quizBody = @{
  title = "Greetings Check"
  instructions = "Choose the best answer for each question."
  sequence = 1
  publish = $true
  questions = @(
    @{
      position = 1
      prompt = "Which phrase means hello?"
      choices = @(
        @{ position = 1; label = "Bonjour"; correct = $true },
        @{ position = 2; label = "Au revoir"; correct = $false }
      )
    },
    @{
      position = 2
      prompt = "Which phrase means goodbye?"
      choices = @(
        @{ position = 1; label = "Merci"; correct = $false },
        @{ position = 2; label = "Au revoir"; correct = $true }
      )
    }
  )
} | ConvertTo-Json -Depth 10

$quiz = Invoke-RestMethod `
  -Method Post `
  -Uri "$base/admin/lessons/$lessonId/quizzes" `
  -Headers $adminHeaders `
  -ContentType "application/json" `
  -Body $quizBody

$quizId = $quiz.data.id
```

Expected outcome: creation returns `201`; an invalid quiz with no questions or multiple correct choices returns `400`.

## 9. Browse and complete the lesson

```powershell
$lessons = Invoke-RestMethod `
  -Method Get `
  -Uri "$base/lessons?page=0&size=20" `
  -Headers $learnerHeaders

$lessonDetail = Invoke-RestMethod `
  -Method Get `
  -Uri "$base/lessons/$lessonId" `
  -Headers $learnerHeaders

Invoke-RestMethod `
  -Method Put `
  -Uri "$base/lessons/$lessonId/completion" `
  -Headers $learnerHeaders

Invoke-RestMethod `
  -Method Put `
  -Uri "$base/lessons/$lessonId/completion" `
  -Headers $learnerHeaders
```

Expected outcome:

- The catalog contains the lesson in sequence with no persistence-only fields.
- Detail includes lesson content.
- Both completion calls succeed with `204`.
- Only one completion exists and progress is not double-counted.

## 10. Retrieve and submit the quiz

```powershell
$quizForLearner = Invoke-RestMethod `
  -Method Get `
  -Uri "$base/quizzes/$quizId" `
  -Headers $learnerHeaders

$q1 = $quizForLearner.data.questions[0]
$q2 = $quizForLearner.data.questions[1]

$submission = @{
  answers = @(
    @{ questionId = $q1.id; choiceId = $q1.choices[0].id },
    @{ questionId = $q2.id; choiceId = $q2.choices[1].id }
  )
} | ConvertTo-Json -Depth 6

$idempotencyKey = [guid]::NewGuid().ToString()
$attemptHeaders = $learnerHeaders.Clone()
$attemptHeaders["Idempotency-Key"] = $idempotencyKey

$attempt = Invoke-RestMethod `
  -Method Post `
  -Uri "$base/quizzes/$quizId/attempts" `
  -Headers $attemptHeaders `
  -ContentType "application/json" `
  -Body $submission

$retriedAttempt = Invoke-RestMethod `
  -Method Post `
  -Uri "$base/quizzes/$quizId/attempts" `
  -Headers $attemptHeaders `
  -ContentType "application/json" `
  -Body $submission
```

Expected outcome:

- Pre-attempt quiz data contains no `correct` indicators.
- The submitted score is `100.00` with two correct question outcomes.
- Retrying with the same idempotency key returns the same attempt ID and creates no duplicate.
- An incomplete answer list or a choice from another question returns `400` and stores nothing.

## 11. Review attempts and progress

```powershell
$attempts = Invoke-RestMethod `
  -Method Get `
  -Uri "$base/attempts?size=20" `
  -Headers $learnerHeaders

$attemptDetail = Invoke-RestMethod `
  -Method Get `
  -Uri "$base/attempts/$($attempt.data.id)" `
  -Headers $learnerHeaders

$progress = Invoke-RestMethod `
  -Method Get `
  -Uri "$base/progress/me" `
  -Headers $learnerHeaders
```

Expected outcome:

- Attempt history contains one result for the idempotently retried submission.
- Attempt detail includes immutable question and choice outcome snapshots.
- Progress reports one available lesson, one completed lesson, `100.00` lesson completion, one quiz attempt, and a `100.00` latest/best score.

## 12. Validate ownership and token failures

Register and log in a second learner, then request the first learner's attempt ID:

```powershell
$otherRegistration = @{
  email = "other@example.test"
  password = "Other-local-123!"
} | ConvertTo-Json

Invoke-RestMethod -Method Post -Uri "$base/auth/register" `
  -ContentType "application/json" -Body $otherRegistration
$otherLogin = Invoke-RestMethod -Method Post -Uri "$base/auth/login" `
  -ContentType "application/json" -Body $otherRegistration
$otherHeaders = @{ Authorization = "Bearer $($otherLogin.data.accessToken)" }

try {
  Invoke-RestMethod -Method Get `
    -Uri "$base/attempts/$($attempt.data.id)" `
    -Headers $otherHeaders
  throw "Expected cross-user attempt access to fail"
} catch {
  if ($_.Exception.Response.StatusCode.value__ -ne 404) { throw }
}
```

Expected outcome: the resource is reported as `404`, preventing ownership disclosure.

Also verify:

- No bearer token on a protected route returns `401` and a `WWW-Authenticate` header.
- A malformed or expired token returns the same problem shape with a distinct stable error code.
- A valid learner token on an admin route returns `403`.
- Problem responses include `type`, `title`, `status`, `detail`, `instance`, `code`, `traceId`, and `timestamp`.

## 13. Validate historical integrity

1. Update the quiz as administrator with different wording and publish the new revision.
2. Retrieve the previous attempt.
3. Retire the quiz and lesson.
4. Retrieve the previous attempt again.

Expected outcome:

- The existing attempt retains its original title, prompts, selected choices, correct choices, and score.
- Retired content disappears from new learner catalogs and cannot receive new completions or attempts.
- Historical attempts remain reviewable by their owner.

## 14. Stop local services

Stop the application with `Ctrl+C`, then:

```powershell
docker compose down
Remove-Item Env:APP_BOOTSTRAP_ADMIN_PASSWORD -ErrorAction SilentlyContinue
Remove-Item Env:DB_PASSWORD -ErrorAction SilentlyContinue
```

Use `docker compose down -v` only when intentionally deleting all local database data.
