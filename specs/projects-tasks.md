# Projects — Commit-Driven Implementation Plan

**Spec:** [projects-spec.md](projects-spec.md)
**Test plan:** none — checks below are **planned verification** derived from the spec.

## Overview

Create and list projects with unique, immutable keys. This plan also introduces the shared `404` and `422` handling that later specs reuse. This is MR 2 in the global stack (see [authentication-tasks.md](authentication-tasks.md)).

**Estimated size:** ~210–300 source lines — single PR (base: authentication PR 2)

**Merge safety:** Additive. **Known interim gap:** the redirect after create targets `/projects/{key}/issues`, which only exists after issues PR 1 (MR 3). Until then that link returns `404`. This is accepted because the stack is merged in sequence before any release. Tell me if you would rather have a temporary landing page.

## Tasks

### 1. Shared not-found and validation-error handling

**Commit intent:** A reusable way to return a `404` page (full page or htmx fragment) and to re-render a form with field errors as `422`. Existing login behaviour is unchanged.
**Dependencies:** authentication group 3
**Estimated source lines:** ~40–70
**Requirements:** Foundation for PRJ-3, PRJ-4, PRJ-5, PRJ-7

- [x] 1.1 Not-found exception mapped to a `404` error template; htmx requests get a small fragment. Check: test hitting a controller that raises it gets `404` with no stack trace.
- [x] 1.2 Convention or helper for rendering a form view with status `422`. Check: covered by group 3 tests.

### 2. Project persistence

**Commit intent:** A projects table with a unique key, a creation service that turns a unique violation into a "key already in use" validation error, and key-ordered listing.
**Dependencies:** 1
**Estimated source lines:** ~70–100
**Requirements:** PRJ-5, PRJ-6, PRJ-9

- [x] 2.1 Flyway migration: projects (key unique, name, created_at, created_by). Check: migration applies in the Testcontainers test.
- [x] 2.2 Entity, repository (`findByKey`, all ordered by key) and create service that validates and maps the DB unique violation to a field error. Check: concurrency test with two parallel creates using the same key gives one project and one validation error, no `5xx` (PRJ-6).

### 3. Project pages

**Commit intent:** List, create form, create, and `/projects/{key}` redirect.
**Dependencies:** 2
**Estimated source lines:** ~100–130
**Requirements:** PRJ-1, PRJ-2, PRJ-3, PRJ-4, PRJ-5, PRJ-7, PRJ-8

- [x] 3.1 Controller and templates for list and new form, with Bean Validation for key `^[A-Z]{2,10}$` and trimmed name 1–100. Check: tests for PRJ-1, PRJ-3 (lowercase, 1 char, 11 chars, digits), PRJ-4, PRJ-5 (existing project unchanged).
- [x] 3.2 List sorted by key, showing projects from all users. Check: test with projects created by two users (PRJ-2).
- [x] 3.3 `GET /projects/{key}` redirects; an unknown key returns `404`. Check: tests for PRJ-7.
- [x] 3.4 Unauthenticated access. Check: tests for the browser and htmx variants (PRJ-8).
- [x] 3.5 Persistence across a context restart. Check: test (PRJ-9).

## Coverage and ordering

| Requirement | Group |
| --- | --- |
| PRJ-1, PRJ-2, PRJ-3, PRJ-4, PRJ-7, PRJ-8 | 3 |
| PRJ-5 | 2, 3 |
| PRJ-6, PRJ-9 | 2 (PRJ-9 checked in 3) |

The groups run in order 1 → 2 → 3.
