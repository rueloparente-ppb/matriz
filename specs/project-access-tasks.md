# Project Access, Lifecycle and Assignees — Commit-Driven Implementation Plan

**Spec:** [project-access-spec.md](project-access-spec.md)
**Test plan:** none — checks below are **planned verification** derived from the spec.

## Overview

Introduces project ownership/membership-based access, multiple assignees, project edit/delete, issue delete, accessible-project search and a dashboard. Existing modules: `identity`, `projects`, `issues`, `comments`, `board`, `sprints`, `history`, `common` (Spring Modulith, controllers in `web/`, rules in `core/`, public API at module root). Flyway migrations run V1–V7; new ones start at V8. Integration tests extend `AbstractIntegrationTest` (Testcontainers PostgreSQL); `ArchitectureTests` guards module boundaries.

Design proposals (**not confirmed — please confirm or change before approving**):

- **Access resolution without a module cycle.** `projects` cannot call `issues` (issues already depends on projects). `projects` defines a small SPI in its root package (`ProjectMembership`: is user an assignee in a project / which project ids) that `issues` implements. `ProjectsApi` gains an access-aware lookup returning the project plus the caller's role (OWNER, MEMBER) or empty. Every controller that today calls `ProjectsApi.findByKey` (`ProjectController`, `IssueController`, `CommentController`, `BoardController`, `SprintController`, `HistoryController`) switches to it, so the identical `404` comes from one place.
- **Cascade deletion via database `ON DELETE CASCADE`** on the child foreign keys (issue→project, comment/history/assignment/sprint_issue→issue, sprint→project), applied in a single migration, so deletion is atomic with no cross-module listeners. Alternative: synchronous in-transaction delete events consumed by each module (same pattern as `IssueFieldChanged`) — more code, preserves module ownership of deletes. This reverses the "`ON DELETE RESTRICT`, nothing is deleted" rule in `docs/database.md`.
- **Assignees** live in `issues` as an `issue_assignee` table (unique `(issue_id, user_id)`); existing `issue.assignee_id` values are migrated into it and the column dropped. Duplicate-add and concurrency (ASG-2/3) are decided by the unique constraint, mirroring the key-uniqueness handling in `ProjectService.create`.
- **Hard dependency on spec reconciliation:** group 5 applies a 3–100 project-name rule to creation as well, which contradicts `projects-spec.md` (1–100). Spec text of `projects-spec`, `issues-spec` (single assignee), `database.md` is updated in group 10.

**Estimated size:** ~920–1380 source lines — stack of 4 PRs

## PR stack

### PR 1 — Access resolution, multi-assignee model and owner-only rules

**Base:** default branch
**Groups:** 1, 2, 3
**Estimated source lines:** ~350–530
**Merge safety:** Behaviour change is the one the spec defines (non-members get `404`, members cannot mutate). No assignee add/remove UI yet, so membership only arises from migrated data. No toggle is needed; existing tests that assume every user sees every project are updated in the same PR.

### PR 2 — Assignee management and project editing

**Base:** PR 1
**Groups:** 4, 5
**Estimated source lines:** ~240–350
**Merge safety:** Additive endpoints and one nullable column; existing flows unchanged.

### PR 3 — Issue and project deletion

**Base:** PR 2
**Groups:** 6, 7
**Estimated source lines:** ~150–230
**Merge safety:** Additive routes behind owner-only checks plus a migration that changes FK actions; nothing deletes unless the new confirmed `POST` is called.

### PR 4 — Project search, dashboard and end-to-end verification

**Base:** PR 3
**Groups:** 8, 9, 10
**Estimated source lines:** ~180–270
**Merge safety:** Replaces the `/` redirect and the unfiltered list; all dependencies are in earlier PRs.

## Tasks

### 1. Access-aware project lookup

**Commit intent:** One place decides whether the caller can see a project and in which role. Inaccessible and unknown keys are indistinguishable. Project creation and listing still work for owners. Membership SPI is defined but returns "not a member" until group 2.
**PR:** 1
**Dependencies:** None
**Estimated source lines:** ~120–180
**Requirements:** ACC-2, ACC-5 (project key path), ACC-7, ACC-8 (routes already authenticated)
**Affected code:** `projects/ProjectsApi`, `projects/ProjectRef`, `projects/core/ProjectService`, `projects/core/Project` (`createdBy`), web controllers listed above, `common/NotFoundException`

- [x] 1.1 Add `ProjectMembership` SPI and an access-aware lookup plus a role type on the projects public API. Check: unit/integration test — owner gets OWNER, non-owner without membership gets empty.
- [x] 1.2 Switch the six controllers to the new lookup. Check: test — user B requesting any `/projects/{aliceKey}/...` route (issues, comments, board, sprints, history) receives the same `404` as an unknown key (ACC-2).
- [x] 1.3 Test that a posted owner value on project create is ignored (ACC-7); update existing tests that relied on global visibility. Check: `./mvnw verify` green for `ProjectsTests`, `IssuesTests`, `CommentsTests`, `BoardTests`, `SprintsTests`, `HistoryTests`.

### 2. Multiple assignees: persistence and read side

**Commit intent:** An issue has a set of assignees stored in `issue_assignee`; existing single assignees are migrated. List filter, issue detail and board display read from the set. Membership SPI is implemented from this table. No add/remove endpoints yet (the old single-field `assignee` update is removed; clearing/setting is replaced by group 4).
**PR:** 1
**Dependencies:** 1
**Estimated source lines:** ~120–180
**Requirements:** ASG-1 (display of several), ASG-7, ACC-9 (membership derivation)
**Affected code:** new `V8__issue_assignee.sql`, `issues/core/Issue`, `IssueService`, `IssueSpecs`, `AssigneeFilter`, `IssueDetail`, `IssueRef.assigneeId`, `issues/web/IssueController`, `templates/issues/{detail,field,list}.html`, `board/web/BoardController` + `BoardCard`, `IssueForm`

- [x] 2.1 Migration creating `issue_assignee` (unique `(issue_id, user_id)`), copying `assignee_id`, dropping the column. Check: migration test with seeded issue keeps its assignee.
- [x] 2.2 Replace `assigneeId` with an assignee set in entity/DTOs/`IssueRef`; update filter (`assignee=<id>`, `none`) and board/detail rendering. Check: filter tests (`ISS-10`) still pass; issue with two stored assignees shows both.
- [x] 2.3 Implement `ProjectMembership` in `issues`. Check: assigned user can open the project (`200`), unassigned user gets `404` (ACC-9 first half).

### 3. Owner-only mutations and member rights

**Commit intent:** Members can view and comment; every other mutation (issues, board moves, sprints) returns `403` for members and changes nothing. Comment author/time still come from the session and clock.
**PR:** 1
**Dependencies:** 1, 2
**Estimated source lines:** ~80–130
**Requirements:** ACC-3, ACC-4, ACC-6
**Affected code:** `IssueController`, `BoardController` (`move`), `SprintController` (create/start/complete/issue sprint), `CommentController`, `common/GlobalExceptionHandler`, `common/ErrorViews`

- [x] 3.1 Add an owner-required guard (forbidden exception mapped to `403`, htmx-safe) used by all mutation handlers. Check: member `POST` to issue create/field edit, board move, sprint actions each returns `403`; stored data unchanged.
- [x] 3.2 Tests that members can `GET` pages and add/list comments, and a forged `author`/`createdAt` parameter is ignored. Check: `CommentsTests` additions (ACC-3, ACC-6).
- [x] 3.3 Precedence test: non-member gets `404` before `403`; missing CSRF gets `403`. Check: new test.

### 4. Assignee management

**Commit intent:** The owner adds and removes assignees on an issue; eligible users are listed; history records changes.
**PR:** 2
**Dependencies:** 1, 2, 3
**Estimated source lines:** ~150–220
**Requirements:** ASG-1…ASG-6, ASG-8, ACC-9 (revocation)
**Affected code:** `issues/web/IssueController` or new `AssigneeController`, `IssueService`, `IssueRepository`/new assignee repository, `IssueFieldChanged` (or a new assignee-changed event) and `history/core/HistoryRecorder`, `history/core/HistoryEntry` (kind/field check in `V6`), new assignees fragment template, `IdentityApi.listUsers`

- [x] 4.1 Endpoints: `GET/POST .../assignees`, `POST .../assignees/{userId}/delete`; eligible list = users not yet assigned, owner-only. Check: owner adds two users; both shown; member gets `403`; non-member `404`.
- [x] 4.2 Duplicate and invalid input: `422` for duplicate, nonexistent or non-numeric `userId`; `404` removing a non-assignee. Check: tests incl. two parallel identical adds → one row, one `422`, no `5xx` (ASG-3).
- [x] 4.3 History: each add/remove produces an entry with the acting user (ASG-8); check whether the `issue_history` `kind`/`field` constraints need a migration and add it if so. Check: history test.
- [x] 4.4 Revocation: removing the last assignment makes the former member get `404`; their comments remain (ACC-9). Check: test. Persistence after restart (ASG-7) is covered by the DB-backed test reloading the issue.

### 5. Project editing

**Commit intent:** The owner edits project name and description; key is immutable; the name rule is 3–100 characters everywhere.
**PR:** 2
**Dependencies:** 1, 3
**Estimated source lines:** ~90–130
**Requirements:** PRJ-E1…PRJ-E4
**Affected code:** new `V9__project_description.sql`, `Project`, `ProjectService`, `ProjectController`, `ProjectForm`, `templates/projects/{new,edit}.html`, `ProjectRef`

- [x] 5.1 Add nullable `description` (≤1000) and `GET /projects/{key}/edit`, `POST /projects/{key}`. Check: valid edit redirects and shows stored values (PRJ-E1); `key` field ignored (PRJ-E4).
- [x] 5.2 Validation: name 3–100 after trimming, whitespace-only rejected, description ≤1000, applied to create and edit with input preserved. Check: `422` tests (PRJ-E2/E3) and that existing `ProjectsTests` name cases are updated.
- [x] 5.3 Member `POST` → `403`, non-member → `404`. Check: test.

### 6. Issue deletion

**Commit intent:** The owner deletes an issue after confirmation; dependents are removed; numbers are not reused.
**PR:** 3
**Dependencies:** 3, 2
**Estimated source lines:** ~70–110
**Requirements:** ISS-D1, ISS-D2
**Affected code:** new migration setting `ON DELETE CASCADE` on child FKs of `issue` (`comment`, `issue_history`, `issue_assignee`, `sprint_issue`), `IssueService`, `IssueController`, `templates/issues/detail.html` (confirmation), `IssueNumbering`

- [ ] 6.1 Migration for cascading FKs; `POST .../issues/{number}/delete` (owner only) with a confirmation step in the UI. Check: after deletion, no comments, history, assignments or sprint membership rows remain (test queries via repositories) and the issue returns `404`.
- [ ] 6.2 Numbering test: create 1–3, delete 3, create again gets 4 (counter unchanged) (ISS-D2). Check: test.
- [ ] 6.3 Member → `403`; non-member → `404`; htmx request handled via redirect-safe response (`HX-Redirect`). Check: tests with and without `HX-Request`.

### 7. Project deletion

**Commit intent:** The owner deletes a project after a confirmation page; everything below it goes in one transaction.
**PR:** 3
**Dependencies:** 6
**Estimated source lines:** ~80–120
**Requirements:** PRJ-D1…PRJ-D4
**Affected code:** migration cascading `issue`, `issue_counter`, `sprint` FKs to `project`, `ProjectService`, `ProjectController`, new `templates/projects/delete.html`

- [ ] 7.1 `GET /projects/{key}/delete` renders confirmation and deletes nothing (PRJ-D1). Check: test.
- [ ] 7.2 `POST /projects/{key}/delete` removes the project and all dependants, redirects to `/projects` (PRJ-D2, PRJ-D4). Check: seeded project with issues, comments, history, sprint, assignments leaves zero rows.
- [ ] 7.3 Atomicity (PRJ-D3): a simulated failure mid-delete rolls back everything. Check: test using an injected failure in the transaction; the project and children remain.
- [ ] 7.4 Key reuse after delete and owner-only/`403`/`404` behaviour. Check: tests.

### 8. Accessible projects with search and paging

**Commit intent:** `/projects` lists only accessible projects, with name search and paging and an empty state.
**PR:** 4
**Dependencies:** 1, 2
**Estimated source lines:** ~90–140
**Requirements:** ACC-1 (list), SRC-1, SRC-2
**Affected code:** `ProjectsApi`/`ProjectService` search method, `ProjectRepository` (specification or query), `ProjectController.list`, `templates/projects/list.html`, shared page-parsing helper used by `issues/web/IssueListQuery`

- [ ] 8.1 Query: owned or member projects, name contains `q` (case-insensitive), key ascending, 25 per page. Check: tests with two users; non-accessible project never appears for any `q`.
- [ ] 8.2 `page` validation (`422` for non-numeric or <1; empty list past the end) and empty-state message. Check: tests.

### 9. Dashboard

**Commit intent:** `/` shows the user's name and each accessible project with counts per status over all its issues.
**PR:** 4
**Dependencies:** 1, 2, 6
**Estimated source lines:** ~90–130
**Requirements:** DSH-1…DSH-4, ISS-D3
**Affected code:** `common/HomeController` (or a dashboard controller in the owning module), `IssuesApi` (counts per project and status via a grouped query in `IssueRepository`), new `templates/dashboard.html`, `layout/base.html` (user/logout)

- [ ] 9.1 Grouped count query returning zero for missing statuses; dashboard rendered per accessible project with links. Check: test with >25 issues proves counts are not page-limited (DSH-2).
- [ ] 9.2 Counts update after status change, create and delete on the next request. Check: tests (DSH-3, ISS-D3).
- [ ] 9.3 Empty state and no leakage of other users' projects. Check: test (DSH-4).

### 10. End-to-end verification and documentation

**Commit intent:** Cross-cutting authorization tests and reconciliation of owning docs with the new behaviour.
**PR:** 4
**Dependencies:** 1–9
**Estimated source lines:** ~0 (tests and docs)
**Requirements:** ACC-5, ACC-8; consistency of related specs

- [ ] 10.1 Parameterised test: for every route in the spec and existing project routes, unauthenticated → login redirect/`401`; user B with changed key/number/userId → `404`. Check: new test class (ACC-5, ACC-8).
- [ ] 10.2 Update `projects-spec.md`, `issues-spec.md`, `database.md`, `openapi.yaml` and README for ownership, assignees, deletion and the name rule. Check: manual diff review against `project-access-spec.md`; no remaining "nothing is deleted"/single-assignee statements.
- [ ] 10.3 Run `./mvnw verify` (includes `ArchitectureTests` and module tests). Check: green output recorded.

## Coverage and ordering

| Requirement | Group(s) |
| --- | --- |
| ACC-1 | 1, 8, 9 |
| ACC-2, ACC-5 | 1, 10 |
| ACC-3, ACC-4, ACC-6 | 3 (+ 4, 5, 6, 7 owner checks) |
| ACC-7 | 1 |
| ACC-8 | 10 |
| ACC-9 | 2, 4, 6 |
| PRJ-E1…E4 | 5 |
| PRJ-D1…D4 | 7 |
| ISS-D1, D2 / D3 | 6 / 9 |
| ASG-1…6, ASG-8 / ASG-7 | 4 / 2, 4 |
| SRC-1, SRC-2 | 8 |
| DSH-1…4 | 9 |

Parallel work: after PR 1, groups 4 and 5 are independent; 6 can start once 3 is done. No group depends on a group in a later PR.

**Open points before approval:** (a) confirm the SPI approach for access resolution; (b) confirm DB cascade vs delete events; (c) confirm that members may not change issue status (spec as written), which affects the board for assignees; (d) PR 1 is the largest and touches six controllers; say if you prefer to split group 1 into its own PR.
