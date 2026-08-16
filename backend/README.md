# French Learning Backend

Spring Boot backend for secure learner accounts, French lessons, quizzes, and
personal progress. The service uses Java 21, Maven, and PostgreSQL 16.

The project is under active development. The setup below describes the current
local development workflow; feature endpoints are added by later implementation
tasks.

## Prerequisites

- Java 21
- Docker with Docker Compose
- OpenSSL for local JWT key generation
- PowerShell 7+ for the Windows commands below

Maven does not need to be installed separately because the project includes the
Maven Wrapper.

## Local setup

Create a local environment file from the safe example and replace its
placeholder database password:

```powershell
Copy-Item .env.example .env
```

Generate local-only RSA keys at the paths used by the `local` Spring profile:

```powershell
New-Item -ItemType Directory -Force -Path .local\keys | Out-Null
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 -out .local\keys\jwt-private.pem
openssl rsa -pubout -in .local\keys\jwt-private.pem -out .local\keys\jwt-public.pem
```

Do not commit `.env`, `.local/`, or generated private keys. These paths are
excluded by `.gitignore`.

## Configuration

The application reads environment-specific values from environment variables.
The main local settings are documented in `.env.example`:

- `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` configure PostgreSQL.
- `POSTGRES_DB` and `POSTGRES_PORT` configure the local Compose service.
- `JWT_PRIVATE_KEY_PATH` and `JWT_PUBLIC_KEY_PATH` locate signing keys.
- `JWT_ISSUER` and `JWT_AUDIENCE` identify locally issued access tokens.

Docker Compose automatically reads `.env`. Before starting Spring Boot, export
the same application values in the current shell. For example:

```powershell
$env:DB_URL = "jdbc:postgresql://localhost:5432/french_learning"
$env:DB_USERNAME = "french_app"
$env:DB_PASSWORD = "<the same password configured in .env>"
$env:JWT_PRIVATE_KEY_PATH = "$PWD\.local\keys\jwt-private.pem"
$env:JWT_PUBLIC_KEY_PATH = "$PWD\.local\keys\jwt-public.pem"
$env:JWT_ISSUER = "http://localhost:8080"
$env:JWT_AUDIENCE = "french-learning-api"
```

## Start PostgreSQL

```powershell
docker compose up -d postgres
docker compose ps
```

Wait until the `postgres` service reports healthy.

## Build and test

On Windows:

```powershell
.\mvnw.cmd clean verify
```

On macOS or Linux:

```sh
./mvnw clean verify
```

Unit tests named `*Test` run with Surefire. Integration tests named `*IT` run
with Failsafe during `verify`, and JaCoCo produces the coverage report when test
execution data is available.

## Run locally

Start PostgreSQL, export the application environment variables, and run the
service with the `local` profile:

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local"
```

The application listens on `http://localhost:8080` by default. Stop the
application with `Ctrl+C`, then stop PostgreSQL:

```powershell
docker compose down
```

## Project references

- [Detailed quickstart and acceptance validation](specs/001-french-learning-backend/quickstart.md)
- [Committed OpenAPI contract](specs/001-french-learning-backend/contracts/openapi.yaml)
