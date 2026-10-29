# Kanban Board — Commit-Driven Implementation Plan

**Spec:** [board-spec.md](board-spec.md)
**Test plan:** none — checks below are **planned verification** derived from the spec.

## Overview

The board has three fixed columns, and drag-and-drop calls the issue status update path from issues group 5. This is MR 6 in the global stack. BRD-9 (history) is delivered by [activity-history-tasks.md](activity-history-tasks.md). The `sprint` filter is delivered by [sprints-tasks.md](sprints-tasks.md).

**Estimated size:** ~200–290 source lines — single PR (base: issues PR 3)

**Merge safety:** Additive page and endpoint. Status rules are unchanged because the board reuses the issue update service.

## Tasks

### 1. Board page

**Commit intent:** `GET /projects/{key}/board` renders three ordered columns with cards.
**Dependencies:** issues group 2
**Estimated source lines:** ~100–140
**Requirements:** BRD-1, BRD-2, BRD-3, BRD-10, BRD-11

- [ ] 1.1 Query grouping the project's issues by status, ordered by number ascending. Check: tests for BRD-1 (no issues from other projects), BRD-3.
- [ ] 1.2 Board template and card fragment (key, title, type, priority, assignee or "Unassigned", link), with empty columns rendered and a navbar link. Check: test for BRD-2; manual visual check.
- [ ] 1.3 Unknown project returns `404`; unauthenticated access. Check: tests for BRD-10, BRD-11.

### 2. Drag-and-drop move

**Commit intent:** `POST /projects/{key}/board/move` changes status through the shared update service and returns the refreshed board fragment.
**Dependencies:** 1, issues group 5
**Estimated source lines:** ~100–150
**Requirements:** BRD-4, BRD-5, BRD-6, BRD-7, BRD-8

- [ ] 2.1 Endpoint: project `404` → issue `404` → status or number `422`; delegates to the status update. Check: tests for BRD-6, BRD-7 (an issue of another project is unchanged), and the precedence order.
- [ ] 2.2 Return the refreshed board fragment for both success and rejection (with an htmx swap on `422`), so the card snaps back. Check: tests for BRD-4, BRD-5 (detail page shows the new status).
- [ ] 2.3 Moving to the current status is a no-op. Check: test that `updatedAt` is unchanged (BRD-8).
- [ ] 2.4 SortableJS glue (about 15 lines of JS) posting `issue` and `status` with the CSRF header through `htmx.ajax`. Check: manual drag in the browser; JS is excluded from automated tests.

## Coverage and ordering

| Requirement | Group |
| --- | --- |
| BRD-1, BRD-2, BRD-3, BRD-10, BRD-11 | 1 |
| BRD-4, BRD-5, BRD-6, BRD-7, BRD-8 | 2 |
| BRD-9 | activity-history group 2 |
| `sprint` query parameter | sprints group 4 |

The groups run in order 1 → 2.
