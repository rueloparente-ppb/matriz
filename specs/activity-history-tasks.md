# Activity History — Commit-Driven Implementation Plan

**Spec:** [activity-history-spec.md](activity-history-spec.md)
**Test plan:** none — checks below are **planned verification** derived from the spec.

## Overview

An append-only per-issue log. The `history` module listens synchronously (`@EventListener`) to `IssueCreated` and `IssueFieldChanged` (published by issues group 5) and to `IssueSprintChanged` (published by sprints groups 2–3). Because the listener runs in the publisher's transaction, a change and its entry commit together (architecture AD-2). The board inherits this because it goes through `IssuesApi`. This is MR 8 in the global stack.

**Estimated size:** ~210–310 source lines — single PR (base: comments PR)

**Merge safety:** Additive table. Existing changes start writing entries; their responses are unchanged.

## Tasks

### 1. History persistence and recorder

**Commit intent:** A history table and a recorder that must run inside the caller's transaction. Not wired in yet.
**Dependencies:** issues group 1
**Estimated source lines:** ~70–100
**Requirements:** HST-13 (and the atomicity rule)

- [ ] 1.1 Flyway migration: issue_history (issue FK, actor FK, kind, field, old_value text, new_value text, created_at, identity id). Check: migration applies.
- [ ] 1.2 Recorder service that requires an existing transaction (`Propagation.MANDATORY`) and stores the display values it is given. Check: test that calling it outside a transaction fails.
- [ ] 1.3 No update or delete endpoints exist. Check: entries are present after a context restart (HST-13).

### 2. Record changes

**Commit intent:** Issue create writes `CREATED`; every real field change writes one `FIELD_CHANGED` entry. No-ops and rejected changes write nothing.
**Dependencies:** 1, issues group 5, board group 2, comments group 1
**Estimated source lines:** ~50–90
**Requirements:** HST-1, HST-2, HST-3, HST-4, HST-5, HST-6, HST-10, BRD-9

- [ ] 2.1 Synchronous `@EventListener`s for `IssueCreated` and `IssueFieldChanged` that call the recorder. Check: tests for HST-1, HST-2, HST-4, HST-5 (no-op and `422` publish nothing, so nothing is written).
- [ ] 2.2 Assignee values as display names, "None" when empty (resolved by the publisher into the event). Check: tests for assign, reassign and clear (HST-6).
- [ ] 2.3 Board move. Check: test that a move writes the same `status` entry as an edit (HST-3, BRD-9).
- [ ] 2.4 Atomicity. Check: test forcing a recorder failure rolls back the field change.
- [ ] 2.5 Comments. Check: test that adding a comment writes no entry (HST-10).

### 3. History fragment on the detail page

**Commit intent:** Show entries newest first, truncated for display and escaped.
**Dependencies:** 2
**Estimated source lines:** ~70–110
**Requirements:** HST-7, HST-8, HST-9, HST-11, HST-12, HST-14

- [ ] 3.1 `GET /projects/{key}/issues/{number}/history` fragment, also included on the detail page, ordered by created_at desc then id desc. Check: tests for HST-7, HST-8.
- [ ] 3.2 Display truncation to 100 characters; the stored value stays full. Check: test for HST-9.
- [ ] 3.3 Escaping, unknown project or issue, and unauthenticated access. Check: tests for HST-11, HST-12, HST-14.

## Coverage and ordering

| Requirement | Group |
| --- | --- |
| HST-13 | 1 |
| HST-1, HST-2, HST-3, HST-4, HST-5, HST-6, HST-10, BRD-9 | 2 |
| HST-7, HST-8, HST-9, HST-11, HST-12, HST-14 | 3 |
| `SPRINT_CHANGED` entries | listener for `IssueSprintChanged` added with sprints group 2 |

The groups run in order 1 → 2 → 3. Every dependency is in an earlier PR.
