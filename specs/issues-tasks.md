# Issues — Commit-Driven Implementation Plan

**Spec:** [issues-spec.md](issues-spec.md)
**Test plan:** none — checks below are **planned verification** derived from the spec.

## Overview

Issues are MRs 3–5 in the global stack: create and view, then the list with filters and search, then single-field edits with htmx. The spec's "History" note (changes are recorded) is delivered by [activity-history-tasks.md](activity-history-tasks.md), which hooks into the update path built here. Until that lands, edits record nothing.

**Estimated size:** ~560–780 source lines — stack of 3 PRs

## PR stack

### PR 1 (MR 3) — Create and view issues

**Base:** projects PR
**Groups:** 1, 2
**Estimated source lines:** ~230–310
**Merge safety:** Additive. This also gives the projects redirect target (`/projects/{key}/issues`) a page, as a list with no filters or paging until PR 2.

### PR 2 (MR 4) — Issue list, filters and search

**Base:** PR 1
**Groups:** 3, 4
**Estimated source lines:** ~170–250
**Merge safety:** Replaces the minimal list with the full one; no other behaviour changes.

### PR 3 (MR 5) — Inline field editing

**Base:** PR 2
**Groups:** 5, 6
**Estimated source lines:** ~200–280
**Merge safety:** Additive endpoints; the detail page gains edit controls.

## Tasks

### 1. Issue persistence and per-project numbering

**Commit intent:** An issues table and atomic per-project number allocation that is gap-free and never consumed by a failed create.
**PR:** 1
**Dependencies:** projects group 2
**Estimated source lines:** ~80–110
**Requirements:** ISS-5, ISS-6 (part of ISS-1, ISS-3)

- [ ] 1.1 Flyway migration: issues (project FK, number, unique (project, number), title, description, type, priority, status, assignee FK nullable, reporter FK, created_at, updated_at) and `issue_counter` (project_id PK, last_number), as in [database.md](../docs/database.md). Check: migration applies.
- [ ] 1.2 Number allocation inside the create transaction, after validation, by an atomic upsert on `issue_counter` (the `issues` module never writes `project`, see architecture AD-5). Check: concurrency test with N parallel creates gives N distinct consecutive numbers (ISS-5); two projects each start at 1 (ISS-6).
- [ ] 1.3 Entity, enums (type, priority, status) and repository lookup by (project key, number). Check: repository test.

### 2. Create form, create and detail page

**Commit intent:** Create issues and view them; minimal list at `/projects/{key}/issues`.
**PR:** 1
**Dependencies:** 1
**Estimated source lines:** ~150–200
**Requirements:** ISS-1, ISS-2, ISS-3, ISS-4, ISS-7, ISS-8, ISS-20, ISS-21

- [ ] 2.1 Create form (assignee picker from users) with validation: trimmed title 1–200, description ≤5000, enum values, existing assignee; `422` re-render. Check: tests for ISS-1, ISS-2, ISS-3 (number not consumed: next create gets the expected number), ISS-4.
- [ ] 2.2 Detail page showing all fields; unknown project or number returns `404`, and a project's key never serves another project's issue. Check: tests for ISS-7, ISS-8.
- [ ] 2.3 Escaped output. Check: test that `<script>` in title and description is rendered escaped (ISS-20).
- [ ] 2.4 Minimal list page (number descending, no filters) and project navigation (List, New issue). Check: smoke test.
- [ ] 2.5 Unauthenticated access. Check: tests (ISS-21).

### 3. List query: filters, search and paging

**Commit intent:** A query that applies status, type, assignee (`none` = unassigned), `q` on title or key, number-descending order and 25-per-page paging, with strict parameter validation.
**PR:** 2
**Dependencies:** 2
**Estimated source lines:** ~110–160
**Requirements:** ISS-9, ISS-10, ISS-11, ISS-12, ISS-13

- [ ] 3.1 Filter parameter parsing: unknown enum, unknown or non-numeric assignee, non-numeric or <1 page → `422`. Check: tests for ISS-12 (the response contains no list).
- [ ] 3.2 Dynamic query (AND of filters; case-insensitive substring on title or the `KEY-n` string), scoped to the project. Check: tests for ISS-9, ISS-10, ISS-11.
- [ ] 3.3 Paging. Check: tests with 26 issues (page 2 has 1) and an out-of-range page (empty, `200`) (ISS-13).

### 4. List page and htmx fragment

**Commit intent:** The list page with filter controls that refresh only the list fragment through htmx.
**PR:** 2
**Dependencies:** 3
**Estimated source lines:** ~60–90
**Requirements:** ISS-14

- [ ] 4.1 Filter form with `hx-get`, `hx-push-url` and a debounced search input; paging links. Check: test that an `HX-Request` gets only the fragment and a normal request the full page (ISS-14).

### 5. Single-field update service

**Commit intent:** One update path per field (title, description, type, priority, status, assignee) with validation, no-op detection and `updatedAt`. Later reused by the board and history.
**PR:** 3
**Dependencies:** 2
**Estimated source lines:** ~120–170
**Requirements:** ISS-15, ISS-16, ISS-17, ISS-18, ISS-19

- [ ] 5.1 Field dispatch: unknown field name → `404`; protected fields have no route. Check: tests for ISS-18.
- [ ] 5.2 Per-field parse and validate, and set only when the value differs (`updatedAt` touched only then). Check: tests for ISS-15, ISS-16 (stored value and `updatedAt` unchanged), ISS-17.
- [ ] 5.3 Assignee `none` or empty clears it; a nonexistent user returns `422`. Check: tests for ISS-19.
- [ ] 5.4 Public `IssuesApi` (find issue, `changeStatus`, `markUpdated`) plus event records `IssueCreated` and `IssueFieldChanged` (ids and display values), published synchronously from create and from real changes only. Check: test with `PublishedEvents` (or an event capture) that a no-op or `422` publishes nothing; exercised by board group 2.

### 6. Inline edit fragments

**Commit intent:** Detail page fields become htmx edit controls that swap their own fragment and show `422` errors inline.
**PR:** 3
**Dependencies:** 5
**Estimated source lines:** ~80–110
**Requirements:** ISS-15, ISS-16 (UI)

- [ ] 6.1 Per-field fragments (view and edit state) and `hx-post` to `/{field}`; htmx config to swap on `422`. Check: test that the response is the fragment with the error message; manual check in the browser.

## Coverage and ordering

| Requirement | Group |
| --- | --- |
| ISS-1, ISS-2, ISS-3, ISS-4, ISS-7, ISS-8, ISS-20, ISS-21 | 2 |
| ISS-5, ISS-6 | 1 |
| ISS-9, ISS-10, ISS-11, ISS-12, ISS-13 | 3 |
| ISS-14 | 4 |
| ISS-15, ISS-16 | 5, 6 |
| ISS-17, ISS-18, ISS-19 | 5 |
| History behavioural note | activity-history groups 2–3 |

Groups 3–4 and 5–6 both depend only on group 2 and could run in parallel. They are stacked as PR 2 then PR 3 to keep a linear stack.
