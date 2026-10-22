# Sprints — Commit-Driven Implementation Plan

**Spec:** [sprints-spec.md](sprints-spec.md)
**Test plan:** none — checks below are **planned verification** derived from the spec.

## Overview

These are MRs 9–10 in the global stack: first the sprint lifecycle (create, list, start, complete), then issue membership and the board filter. History entries use the recorder from activity-history group 1.

**Estimated size:** ~380–540 source lines — stack of 2 PRs

## PR stack

### PR 1 (MR 9) — Sprint lifecycle

**Base:** activity-history PR
**Groups:** 1, 2
**Estimated source lines:** ~230–320
**Merge safety:** Additive page. Until PR 2, no issue can be put in a sprint, so completing a sprint never moves issues. That is consistent with the spec.

### PR 2 (MR 10) — Issue membership and board filter

**Base:** PR 1
**Groups:** 3, 4
**Estimated source lines:** ~150–220
**Merge safety:** Additive endpoint and an optional board parameter; the board without the parameter is unchanged.

## Tasks

### 1. Sprint persistence

**Commit intent:** A sprints table with constraints that enforce per-project name uniqueness and at most one active sprint, plus nullable issue membership.
**PR:** 1
**Dependencies:** issues group 1
**Estimated source lines:** ~40–70
**Requirements:** SPR-2 (uniqueness), SPR-5

- [ ] 1.1 Flyway migration: sprints (project FK, name, state); unique (project, lower(name)); partial unique index on project where state = ACTIVE; `issues.sprint_id` nullable FK. Check: migration test for both constraints.
- [ ] 1.2 Entity, state enum and repository. Check: repository test.

### 2. Sprint lifecycle service and page

**Commit intent:** Create, list with counts, start, and complete (which removes non-`DONE` issues with history), using the precedence `404` → `422` → `409`.
**PR:** 1
**Dependencies:** 1, activity-history group 1
**Estimated source lines:** ~190–250
**Requirements:** SPR-1, SPR-2, SPR-3, SPR-4, SPR-5, SPR-6, SPR-7, SPR-8, SPR-17, SPR-18

- [ ] 2.1 Create with trimmed name 1–100; a duplicate (from the DB constraint) gives `422`; the same name in another project is accepted. Check: tests for SPR-1, SPR-2.
- [ ] 2.2 Start and complete transitions; an invalid transition gives `409`; the active-sprint index violation gives `409`. Check: tests for SPR-3, SPR-4, SPR-6, plus a concurrent start test (SPR-5).
- [ ] 2.3 Completion clears `sprint_id` on non-`DONE` issues and records `SPRINT_CHANGED` for each, in one transaction. Check: tests for SPR-7, SPR-8 (seed membership directly in test setup, because the endpoint arrives in group 3).
- [ ] 2.4 Sprints page (list with state and issue count, create form, start and complete buttons), a navbar link, unknown project and unauthenticated access. Check: tests for SPR-17, SPR-18.

### 3. Issue sprint assignment

**Commit intent:** `POST /projects/{key}/issues/{number}/sprint` and a picker on the issue detail page.
**PR:** 2
**Dependencies:** 2
**Estimated source lines:** ~100–140
**Requirements:** SPR-9, SPR-10, SPR-11, SPR-12, SPR-13, SPR-14

- [ ] 3.1 Resolve the issue and sprint (a sprint of another project gives `404`); a `COMPLETED` sprint gives `422`; `none` or empty clears it; the current sprint is a no-op. Check: tests for SPR-9, SPR-10, SPR-11, SPR-12, SPR-13.
- [ ] 3.2 Record `SPRINT_CHANGED` with old and new sprint names, and update `updatedAt` only on change. Check: test for SPR-14.
- [ ] 3.3 Picker fragment on the detail page (planned and active sprints of the project, plus "None"). Check: manual check in the browser.

### 4. Board sprint filter

**Commit intent:** `GET /projects/{key}/board?sprint=<id|none>` filters the cards, and the board header gets a sprint selector.
**PR:** 2
**Dependencies:** 1, board group 1
**Estimated source lines:** ~50–80
**Requirements:** SPR-15, SPR-16

- [ ] 4.1 Parse `sprint`: an id from another project gives `404`; any other invalid value gives `422`, with no unfiltered board returned. Check: tests for SPR-15, SPR-16.
- [ ] 4.2 Selector that keeps the filter after a drag-and-drop refresh. Check: test that a move with the filter active returns the filtered fragment.

## Coverage and ordering

| Requirement | Group |
| --- | --- |
| SPR-1, SPR-2, SPR-3, SPR-4, SPR-5, SPR-6, SPR-7, SPR-8, SPR-17, SPR-18 | 2 (SPR-2 and SPR-5 constraints in 1) |
| SPR-9, SPR-10, SPR-11, SPR-12, SPR-13, SPR-14 | 3 |
| SPR-15, SPR-16 | 4 |

Groups 3 and 4 are independent and could run in parallel. Every dependency is in an earlier PR.
