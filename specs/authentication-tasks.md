# Authentication — Commit-Driven Implementation Plan

**Spec:** [authentication-spec.md](authentication-spec.md)
**Test plan:** none — checks below are **planned verification** derived from the spec.

## Overview

The repository has no code yet, so this plan names responsibilities rather than classes. It starts with the project skeleton (MR 0), which the MVP budget does not count, then adds seeded users and form login.

Global MVP stack order (each spec's first PR is based on the previous spec's last PR):

| MR | Plan |
| --- | --- |
| 0–1 | authentication |
| 2 | projects |
| 3–5 | issues |
| 6 | board |
| 7 | comments |
| 8 | activity-history |
| 9–10 | sprints |

Budget: ~300 production lines per PR (the user's budget, which replaces the skill default of 600). Tests, migrations' test data, templates' tests and docs are not counted.

**Estimated size:** skeleton (not counted) + ~180–260 source lines — stack of 2 PRs

## PR stack

### PR 1 (MR 0) — Runnable skeleton

**Base:** default branch
**Groups:** 1
**Estimated source lines:** not counted (skeleton, per user decision)
**Merge safety:** No user-facing behaviour; the app starts and connects to Postgres.

### PR 2 (MR 1) — Seeded users and form login

**Base:** PR 1
**Groups:** 2, 3
**Estimated source lines:** ~180–260
**Merge safety:** Additive; there are no other pages to break. After merge every page requires login.

## Tasks

### 1. Project skeleton

**Commit intent:** A Java 25 / Spring Boot / Maven app that starts against docker-compose Postgres, runs Flyway, and renders a base layout. No feature behaviour.
**PR:** 1
**Dependencies:** None
**Estimated source lines:** not counted
**Requirements:** Foundation — no spec requirement

- [ ] 1.1 Maven project (wrapper, Java 25, Spring Boot web, Thymeleaf, Security, Data JPA, Validation, Flyway, PostgreSQL driver, Testcontainers). Check: `./mvnw verify` succeeds.
- [ ] 1.2 `docker-compose.yml` with PostgreSQL and matching `application` config. Check: `docker compose up -d` then the app starts and Flyway reports an empty schema history.
- [ ] 1.3 Base Thymeleaf layout loading Bootstrap 5, htmx and SortableJS from CDN, with a navbar slot. Check: rendering test sees the three asset tags.
- [ ] 1.4 Shared Testcontainers integration test base (Postgres container, MockMvc). Check: context-load test passes.
- [ ] 1.5 CI workflow running `./mvnw verify`. **Open question:** which CI (GitHub Actions or GitLab CI)? Check: pipeline green on the PR.
- [ ] 1.6 README: prerequisites, `docker compose up`, run command. Check: steps reproduced on a clean checkout.

### 2. User persistence and seeded users

**Commit intent:** A users table with case-insensitive unique usernames, display names and bcrypt hashes, plus seeded users. No login yet.
**PR:** 2
**Dependencies:** 1
**Estimated source lines:** ~60–90
**Requirements:** AUTH-7, AUTH-8 (lookup part)

- [ ] 2.1 Flyway migration for users (unique index on lower(username), display name, password hash). Check: migration test inserts two usernames differing only by case and expects a unique violation.
- [ ] 2.2 Seed migration with a few users and bcrypt hashes; document seeded credentials in the README. Check: test reads seeded users and asserts stored hash ≠ plain password and matches with bcrypt.
- [ ] 2.3 User entity, repository with case-insensitive lookup, and a user-details service. Check: test finds `ALICE` when stored as `alice`.

### 3. Form login, logout, CSRF and htmx handling

**Commit intent:** Every page except `/login` and static assets requires a session; login, logout, CSRF, and the htmx `401` + `HX-Redirect` behaviour.
**PR:** 2
**Dependencies:** 2
**Estimated source lines:** ~120–170
**Requirements:** AUTH-1, AUTH-2, AUTH-3, AUTH-4, AUTH-5, AUTH-6, AUTH-8

- [ ] 3.1 Security configuration: form login at `/login`, failure URL `/login?error`, logout `POST /logout` → `/login?logout`, saved-request redirect, bcrypt encoder. Check: tests for AUTH-1, AUTH-2 (wrong password and unknown user give identical responses), AUTH-5.
- [ ] 3.2 Authentication entry point: `HX-Request: true` → `401` with `HX-Redirect: /login`, otherwise `302` to `/login`; no body content. Check: tests for AUTH-3, AUTH-4.
- [ ] 3.3 CSRF token exposed in the layout and sent by htmx on every request (`hx-headers`). Check: test that a POST without a token returns `403` and changes nothing (AUTH-6); manual check that an htmx POST succeeds.
- [ ] 3.4 Login page template, navbar logout button and display name, `GET /` → `/projects`. Check: test that `/` redirects; manual check of the login page.
- [ ] 3.5 Restart persistence. Check: test that logs in with a seeded user against a fresh context (AUTH-7).

## Coverage and ordering

| Requirement | Group |
| --- | --- |
| AUTH-1, AUTH-2, AUTH-5 | 3 |
| AUTH-3, AUTH-4 | 3 |
| AUTH-6 | 3 |
| AUTH-7 | 2, 3 |
| AUTH-8 | 2, 3 |

The groups run strictly in order 1 → 2 → 3. No group depends on a later PR.
