# Sprints

## Overview

A sprint is a named, time-boxed grouping of issues within a project. A sprint is planned, started and completed by any authenticated user (see [`authentication-spec.md`](./authentication-spec.md)). An issue belongs to at most one sprint, and the board can be filtered by sprint. This is a minimal sprint model: no goals, dates, capacity or burndown.

## HTTP API

| Method | Path | Description |
| --- | --- | --- |
| `GET` | `/projects/{key}/sprints` | Lists the project's sprints with state and issue count |
| `POST` | `/projects/{key}/sprints` | Creates a sprint from `name` (state `PLANNED`) |
| `POST` | `/projects/{key}/sprints/{id}/start` | `PLANNED` → `ACTIVE` |
| `POST` | `/projects/{key}/sprints/{id}/complete` | `ACTIVE` → `COMPLETED` |
| `POST` | `/projects/{key}/issues/{number}/sprint` | Field `sprint` (sprint id, or `none`) sets the issue's sprint |

The board filter (`sprint` query parameter) is on `GET /projects/{key}/board`, rendered as defined in [`board-spec.md`](./board-spec.md).

## Response Codes

| Code | Meaning |
| --- | --- |
| `200 OK` | Page or fragment rendered, action applied |
| `404 Not Found` | Unknown project, sprint (or sprint of another project), or issue |
| `409 Conflict` | Invalid state transition, or starting a sprint while another is active in the project |
| `422 Unprocessable Entity` | Invalid name; sprint `COMPLETED`; or invalid `sprint` filter / field value |

## Behavioural Notes

- **States** — `PLANNED` → `ACTIVE` → `COMPLETED`, one way only. Any other transition returns `409` and changes nothing.
- **One active sprint** — At most one `ACTIVE` sprint per project. Starting a second returns `409`. Concurrent starts yield exactly one success.
- **Name** — 1 to 100 characters after trimming, unique within the project case-insensitively.
- **Membership** — An issue is in zero or one sprint, always in a sprint of its own project. A sprint of another project returns `404`. A `COMPLETED` sprint cannot receive issues (`422`).
- **Completion** — Completing moves every issue in the sprint whose status is not `DONE` out of the sprint (no sprint); `DONE` issues stay as a record. Each moved issue gets a `SPRINT_CHANGED` history entry.
- **Board filter** — `sprint=<id>` shows only issues in that sprint; `sprint=none` only issues in no sprint; absent shows all issues. Any other value returns `422`; an id from another project returns `404`.
- **Precedence** — `404` before `422` before `409`.

## Requirements

| ID | Category | Requirement |
| --- | --- | --- |
| `SPR-1` | Basic | A valid name creates a `PLANNED` sprint in the project and lists it |
| `SPR-2` | Validation | A blank name, one over 100 characters, or a duplicate (case-insensitive) within the project returns `422` and creates nothing; the same name in another project is accepted |
| `SPR-3` | Basic | Starting a `PLANNED` sprint makes it `ACTIVE` |
| `SPR-4` | Conflict | Starting a sprint while another sprint in the project is `ACTIVE` returns `409`; both keep their state |
| `SPR-5` | Concurrency | Concurrent starts of two sprints in one project yield exactly one `ACTIVE` sprint and one `409` |
| `SPR-6` | Conflict | Starting a non-`PLANNED` sprint, or completing a non-`ACTIVE` sprint, returns `409` and changes nothing |
| `SPR-7` | Basic | Completing an `ACTIVE` sprint makes it `COMPLETED`, removes non-`DONE` issues from it and keeps `DONE` issues in it |
| `SPR-8` | Basic | Each issue removed on completion writes a `SPRINT_CHANGED` history entry; kept issues write none |
| `SPR-9` | Basic | Setting an issue's sprint to a `PLANNED` or `ACTIVE` sprint of the same project assigns it; moving it between sprints replaces the old membership |
| `SPR-10` | Basic | `sprint=none` or an empty value removes the issue from its sprint |
| `SPR-11` | Validation | Assigning to a `COMPLETED` sprint returns `422` and changes nothing |
| `SPR-12` | Validation | A sprint id from another project returns `404`; the issue is never added to it |
| `SPR-13` | Basic | Assigning to the issue's current sprint changes nothing, including `updatedAt`, and writes no history |
| `SPR-14` | Basic | A sprint assignment change writes a `SPRINT_CHANGED` history entry with old and new sprint names |
| `SPR-15` | Board | `sprint=<id>` shows only that sprint's issues; `sprint=none` only issues without a sprint; no parameter shows all |
| `SPR-16` | Board | An invalid `sprint` value returns `422`; the board is not returned unfiltered |
| `SPR-17` | Basic | The sprint list shows each sprint's state and issue count; unknown project returns `404` |
| `SPR-18` | Authentication | Unauthenticated requests receive the responses defined in the authentication spec; no sprint data is disclosed |

## Out of Scope

- Sprint goals, start and end dates, capacity, velocity, burndown, carrying issues over automatically, deleting or renaming sprints, reopening a completed sprint, backlog view.

## Related Specs

- [`issues-spec.md`](./issues-spec.md) — issue fields; sprint membership is the only issue attribute this spec owns.
- [`board-spec.md`](./board-spec.md) — owns board rendering.
- [`activity-history-spec.md`](./activity-history-spec.md) — owns `SPRINT_CHANGED` entry format.
