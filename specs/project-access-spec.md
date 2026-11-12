# Project Access, Lifecycle and Assignees

## Overview

Every project has one **owner**, the user who created it. A project is accessible to its owner and to every user who is an assignee of at least one of its issues (a **member**). Users who are neither see the project as if it did not exist. The owner can edit and delete the project, delete issues, and manage assignees; members can view the project and its issues and add comments. An issue has zero or more assignees. The home page is a dashboard of the accessible projects with issue counts per status.

Authentication is required on every endpoint (see [`authentication-spec.md`](./authentication-spec.md)). The acting user always comes from the session; no request parameter names the owner, comment author or acting user.

## Access Rules

| Action | Owner | Member | Other user |
| --- | --- | --- | --- |
| See the project in `/projects` and on the dashboard | yes | yes | no |
| View project, issue list, issue detail, board, sprints, history | yes | yes | `404` |
| Add and list comments | yes | yes | `404` |
| Edit or delete the project | yes | `403` | `404` |
| Create, edit, move (including board and sprint changes) or delete issues | yes | `403` | `404` |
| Create, start or complete sprints | yes | `403` | `404` |
| Add or remove assignees, list eligible users | yes | `403` | `404` |

## HTTP API

| Method | Path | Description |
| --- | --- | --- |
| `GET` | `/` | Dashboard: authenticated user's display name and, for each accessible project, issue counts per status |
| `GET` | `/projects` | Accessible projects. Query: `q` (name search), `page` |
| `GET` | `/projects/{key}/edit` | Edit form (owner only) |
| `POST` | `/projects/{key}` | Updates `name` and `description` (owner only) |
| `GET` | `/projects/{key}/delete` | Delete confirmation page (owner only) |
| `POST` | `/projects/{key}/delete` | Deletes the project and everything in it (owner only) |
| `POST` | `/projects/{key}/issues/{number}/delete` | Deletes the issue (owner only) |
| `GET` | `/projects/{key}/issues/{number}/assignees` | Assignee fragment: current assignees and, for the owner, the eligible users |
| `POST` | `/projects/{key}/issues/{number}/assignees` | Adds the assignee given by `userId` (owner only) |
| `POST` | `/projects/{key}/issues/{number}/assignees/{userId}/delete` | Removes that assignee (owner only) |

Mutations are `POST` so they work from native forms; htmx enhances them and swaps the smallest affected fragment.

## Response Codes

| Code | Meaning |
| --- | --- |
| `200 OK` | Page or fragment rendered |
| `302 Found` | Project or issue deleted (to `/projects` or the project's issue list), project edited (to the project), or full-page mutation completed |
| `403 Forbidden` | Member attempting an owner-only action, or a missing or invalid CSRF token |
| `404 Not Found` | Project does not exist or is not accessible to the caller; unknown issue, user or assignment |
| `422 Unprocessable Entity` | Validation failure; the form or fragment is re-rendered with the error and its input preserved, and nothing is changed |

## Behavioural Notes

- **No disclosure** — An unknown project key and an inaccessible project key produce identical `404` responses, so a non-member cannot learn which keys exist. The same applies to issues and comments, which are always resolved through an accessible project; changing identifiers in a direct request never reaches another project's data.
- **Precedence** — Unauthenticated (authentication spec) → `404` for unknown or inaccessible project, issue, or assignment → `403` for owner-only action by a member → `422` validation. A CSRF failure is `403` and is evaluated before everything after authentication.
- **Eligibility** — Any registered user may be assigned. Assignment is what grants membership, so eligibility cannot depend on prior membership. Users already assigned to the issue are not offered again.
- **Membership is derived** — There is no membership table. A user is a member while they are assignee of at least one issue in the project; removing their last assignment (or deleting that issue) ends their access. Comments and history they authored remain.
- **Ownership is immutable** — The owner cannot change and cannot be an assignee-only member; the owner always has access regardless of assignments.
- **Assignees** — An issue may have many assignees. Adding a user already assigned to the same issue returns `422` and changes nothing; two simultaneous identical adds produce one assignment and one `422`, never `5xx`. Removing a user who is not an assignee returns `404`. Assignee changes are recorded in history per [`activity-history-spec.md`](./activity-history-spec.md).
- **Project fields** — `name` is 3–100 characters after trimming; `description` is optional, up to 1000 characters. The key never changes. Name need not be unique. Setting values equal to the stored ones changes nothing.
- **Project search** — `q` is a case-insensitive substring match on the project name; blank `q` means no restriction. Results are limited to accessible projects, sorted by key ascending, 25 per page; `page` is 1-based, an out-of-range page renders an empty list, and a non-numeric or <1 `page` returns `422`.
- **Deletion** — The confirmation page names what will be removed and is the only way to reach the deleting `POST` from the UI. Deleting a project removes, in one transaction, its issues, comments, history entries, sprints, sprint memberships and assignments; on any failure nothing is removed. Deleting an issue removes its comments, history entries, assignments and sprint membership. No dependent row survives its parent. Issue numbers of a project are never reused after an issue is deleted, and a deleted project's key may be reused by a later project.
- **Dashboard counts** — For each accessible project, counts are over all of the project's issues per status (`TO_DO`, `IN_PROGRESS`, `DONE`), not over a visible page, and a status with no issues shows `0`. The counts reflect the state at request time; mutations never require a manual reload because the affected fragment is re-rendered from the stored data.
- **Empty states** — A user with no accessible projects sees an empty-state message on the dashboard and on `/projects`.

## Requirements

| ID | Category | Requirement |
| --- | --- | --- |
| `ACC-1` | Access | `GET /projects` and the dashboard list only projects the caller owns or is a member of; no other project key or name appears |
| `ACC-2` | Access | Any project, issue, comment, board, sprint or history route for a project the caller cannot access returns `404`, identical to the response for an unknown key |
| `ACC-3` | Access | A member can view the project and issues and add and list comments |
| `ACC-4` | Access | A member attempting any owner-only action in the Access Rules table returns `403` and changes no state |
| `ACC-5` | Access | Changing the project key, issue number or user id in a direct request never returns or changes data of a project the caller cannot access |
| `ACC-6` | Access | A new comment's author and timestamp are set from the session and the server clock; any submitted author or date value is ignored |
| `ACC-7` | Access | The owner of a new project is the authenticated creator; a submitted owner value is ignored |
| `ACC-8` | Authentication | Every endpoint in this spec returns the unauthenticated responses of the authentication spec and discloses no data |
| `ACC-9` | Access | A user who becomes the assignee of an issue gains access to its project; after the last assignment is removed or the issue is deleted they receive `404` for that project |
| `PRJ-E1` | Project edit | The owner can update `name` and `description`; the response redirects to the project and the stored values are shown afterwards |
| `PRJ-E2` | Validation | A name shorter than 3 or longer than 100 characters after trimming, or a whitespace-only name, returns `422`; the entered values are preserved and nothing changes |
| `PRJ-E3` | Validation | A description over 1000 characters returns `422`; nothing changes |
| `PRJ-E4` | Project edit | A submitted `key` is ignored; the key is unchanged |
| `PRJ-D1` | Project delete | `GET /projects/{key}/delete` renders a confirmation page and deletes nothing |
| `PRJ-D2` | Project delete | `POST /projects/{key}/delete` by the owner removes the project, its issues, comments, history entries, sprints, sprint memberships and assignments, and redirects to `/projects`; afterwards the project returns `404` |
| `PRJ-D3` | Project delete | If any part of the deletion fails, no project data is removed |
| `PRJ-D4` | Project delete | After deletion no row referencing the project or its issues remains |
| `ISS-D1` | Issue delete | `POST .../issues/{number}/delete` by the owner removes the issue with its comments, history entries, assignments and sprint membership, and redirects to the project's issue list |
| `ISS-D2` | Issue delete | After deletion the issue returns `404`, other issues keep their numbers, and the next created issue receives a number greater than any previously used |
| `ISS-D3` | Issue delete | Project dashboard counts and the issue list no longer include the deleted issue on the next request |
| `ASG-1` | Assignees | The owner can add several distinct assignees to one issue; the issue detail lists all of them |
| `ASG-2` | Assignees | Adding an already-assigned user returns `422`, creates no second assignment and leaves existing assignments unchanged |
| `ASG-3` | Assignees | Two simultaneous identical adds yield exactly one assignment and one `422`; neither returns `5xx` |
| `ASG-4` | Assignees | Adding a nonexistent `userId` or a non-numeric one returns `422`; nothing changes |
| `ASG-5` | Assignees | The owner can remove one assignee without affecting the others; removing a non-assignee returns `404` |
| `ASG-6` | Assignees | The eligible-user list contains every registered user not already assigned, and is visible only to the owner |
| `ASG-7` | Assignees | Assignments persist after the issue is reopened and after an application restart |
| `ASG-8` | History | Each add and each removal of an assignee is recorded in the issue history with the acting user |
| `SRC-1` | Search | `GET /projects?q=` returns accessible projects whose name contains `q`, case-insensitively; a project the caller cannot access never appears, whatever `q` is |
| `SRC-2` | Search | Blank `q` returns all accessible projects; a non-numeric or <1 `page` returns `422`; an out-of-range page returns an empty list |
| `DSH-1` | Dashboard | `GET /` shows the authenticated user's display name and each accessible project with a link to it and counts for `TO_DO`, `IN_PROGRESS` and `DONE` |
| `DSH-2` | Dashboard | Counts cover all issues of the project, not a page, and show `0` for an empty status |
| `DSH-3` | Dashboard | After an issue's status changes or the issue is created or deleted, the next dashboard request shows the updated counts |
| `DSH-4` | Dashboard | A user with no accessible projects sees an empty-state message and no other user's data |

## Out of Scope

- Project roles beyond owner and member, ownership transfer, invitations and a membership table.
- Public registration rules and password recovery — see [`authentication-spec.md`](./authentication-spec.md).
- Soft delete, undo and archiving of projects or issues.
- Field limits and status values of issues and comments — owned by [`issues-spec.md`](./issues-spec.md) and [`comments-spec.md`](./comments-spec.md).

## Related Specs

- [`authentication-spec.md`](./authentication-spec.md) — session, CSRF and unauthenticated responses.
- [`projects-spec.md`](./projects-spec.md) — project creation and key rules.
- [`issues-spec.md`](./issues-spec.md) — issue fields; its single `assignee` is replaced by the assignees defined here.
- [`comments-spec.md`](./comments-spec.md), [`board-spec.md`](./board-spec.md), [`sprints-spec.md`](./sprints-spec.md), [`activity-history-spec.md`](./activity-history-spec.md) — resources governed by the Access Rules.
