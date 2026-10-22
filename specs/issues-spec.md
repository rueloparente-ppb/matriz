# Issues

## Overview

An issue is a unit of work inside a project. It is created, viewed, listed (with filters and text search) and edited from server-rendered pages; field edits are applied with htmx and swap a fragment in place. Each issue has a human-readable key `{PROJECT_KEY}-{number}`. Authentication is required on every endpoint (see [`authentication-spec.md`](./authentication-spec.md)).

## Fields

| Field | Rules |
| --- | --- |
| `key` | `{PROJECT_KEY}-{number}`, assigned on creation, never changes |
| `number` | Starts at 1 per project, increases by 1, never reused |
| `title` | Required, 1–200 characters after trimming |
| `description` | Optional, up to 5000 characters, plain text (HTML-escaped on output) |
| `type` | `TASK`, `BUG` or `STORY`; default `TASK` |
| `priority` | `LOW`, `MEDIUM` or `HIGH`; default `MEDIUM` |
| `status` | `TO_DO`, `IN_PROGRESS` or `DONE`; starts `TO_DO`; any status may change to any other |
| `assignee` | Optional, any existing user |
| `reporter` | The user who created the issue; never changes |
| `createdAt`, `updatedAt` | Set by the system; `updatedAt` changes only when a field value changes |

## HTTP API

| Method | Path | Description |
| --- | --- | --- |
| `GET` | `/projects/{key}/issues` | Issue list. Query: `status`, `type`, `assignee` (user id or `none`), `q`, `page` |
| `GET` | `/projects/{key}/issues/new` | Create form |
| `POST` | `/projects/{key}/issues` | Creates an issue |
| `GET` | `/projects/{key}/issues/{number}` | Issue detail page |
| `POST` | `/projects/{key}/issues/{number}/{field}` | Updates one field; `field` is `title`, `description`, `type`, `priority`, `status` or `assignee`. Returns the updated field fragment |

## Response Codes

| Code | Meaning |
| --- | --- |
| `200 OK` | Page or fragment rendered, field updated |
| `302 Found` | Issue created, redirects to its detail page |
| `404 Not Found` | Unknown project, unknown issue number within an existing project, or unknown `field` name |
| `422 Unprocessable Entity` | Validation failure; form or field fragment re-rendered with the error, stored value unchanged |

## Behavioural Notes

- **Numbering** — Numbers are allocated atomically per project. Concurrent creates in one project receive distinct, gap-free consecutive numbers; a failed create (validation) does not consume a number.
- **Filters** — Filters combine with AND. An unrecognised `status`, `type` or `assignee` value is a validation error (`422`), not ignored. A missing filter means "no restriction".
- **Search** — `q` is a case-insensitive substring match on title or on the issue key. Blank `q` is no restriction.
- **Order and paging** — Issues sort by number descending, 25 per page; `page` is 1-based, an out-of-range page renders an empty list, and a non-numeric or <1 `page` returns `422`.
- **Edits** — Last write wins; there is no optimistic locking. Setting a field to its current value changes nothing, including `updatedAt`, and records no history.
- **Assignee** — Submitting `none` or an empty value clears the assignee. A nonexistent user id returns `422`.
- **Precedence** — Unknown project returns `404` before any validation is evaluated.
- **Output encoding** — Title and description are always HTML-escaped when rendered.
- **History** — Every changed field value is recorded as defined in [`activity-history-spec.md`](./activity-history-spec.md).

## Requirements

| ID | Category | Requirement |
| --- | --- | --- |
| `ISS-1` | Basic | A valid create returns `302` to the new issue's detail page; the issue has status `TO_DO`, the current user as reporter, and number = previous project maximum + 1 (1 for the first) |
| `ISS-2` | Basic | Omitted type and priority default to `TASK` and `MEDIUM`; omitted assignee and description are empty |
| `ISS-3` | Validation | A blank title, or one over 200 characters after trimming, returns `422`, creates no issue and consumes no number |
| `ISS-4` | Validation | A description over 5000 characters, an invalid type or priority, or a nonexistent assignee returns `422` and creates no issue |
| `ISS-5` | Concurrency | N simultaneous creates in one project produce N issues with distinct consecutive numbers |
| `ISS-6` | Basic | Numbering is independent per project; issue keys never repeat within or across projects |
| `ISS-7` | Basic | The detail page shows key, title, description, type, priority, status, assignee, reporter and timestamps |
| `ISS-8` | Basic | Unknown project key, or unknown issue number, returns `404`; an issue is never served under another project's key |
| `ISS-9` | List | The list shows only that project's issues, number descending, 25 per page |
| `ISS-10` | List | `status`, `type` and `assignee` filters restrict results; combined filters apply AND; `assignee=none` returns unassigned issues |
| `ISS-11` | List | `q` matches title or key case-insensitively as a substring; it combines with filters by AND |
| `ISS-12` | List | An invalid filter value or invalid `page` returns `422`; the list is not returned unfiltered |
| `ISS-13` | List | An out-of-range `page` returns `200` with an empty list |
| `ISS-14` | List | An htmx request to the list returns only the list fragment; a normal request returns the full page |
| `ISS-15` | Edit | A valid single-field update persists the value, returns the updated fragment and sets `updatedAt` |
| `ISS-16` | Edit | An invalid value returns `422` with the error in the fragment; the stored value and `updatedAt` are unchanged |
| `ISS-17` | Edit | Updating a field to its current value changes nothing, including `updatedAt` |
| `ISS-18` | Edit | An unknown `field` name returns `404`; `key`, `number`, `reporter` and timestamps cannot be updated |
| `ISS-19` | Edit | Clearing the assignee with `none` or an empty value leaves the issue unassigned |
| `ISS-20` | Security | A title or description containing HTML or script is rendered escaped and not executed |
| `ISS-21` | Authentication | Unauthenticated requests receive the responses defined in the authentication spec; no issue data is disclosed |

## Out of Scope

- Deleting issues, subtasks, epics, links, labels, attachments, custom fields, custom workflows, rich text or Markdown, optimistic locking, bulk edit.
- Sprint membership of an issue: see [`sprints-spec.md`](./sprints-spec.md).

## Related Specs

- [`projects-spec.md`](./projects-spec.md) — owns project keys.
- [`board-spec.md`](./board-spec.md) — status changes by drag-and-drop.
- [`activity-history-spec.md`](./activity-history-spec.md) — records field changes.
