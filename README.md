# Jira clone

Spring Boot 4 / Java 25 modular monolith with Thymeleaf + htmx. See `docs/` and `specs/`.

## Prerequisites

- JDK 25
- Docker (Postgres via compose; Testcontainers for tests)

## Run

```sh
docker compose up -d postgres      # PostgreSQL on localhost:5432
./mvnw spring-boot:run             # app on http://localhost:8080
```

DB settings default to the compose values; override with `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`.

## Seeded users

Every page except `/login` requires a login. Development users (seeded by Flyway): `alice`, `bob`, `carol`, all with password `password`. Usernames are case-insensitive. These are for local use only.

## Test

```sh
./mvnw verify                      # needs a running Docker daemon
```

## Docker image

```sh
docker build -t jira-clone .
docker compose --profile app up --build   # app + Postgres, http://localhost:8080
```
