# Projects

## Overview

A project groups issues and is identified by a short, unique, immutable key such as `PROJ`. Any authenticated user can create a project and becomes its owner; only the owner and users assigned to its issues can view it. See [`project-access-spec.md`](./project-access-spec.md) for access and lifecycle rules and [`authentication-spec.md`](./authentication-spec.md) for session rules.

## HTTP API

| Method | Path | Auth | Description |
| --- | --- | --- | --- |
| `GET` | `/projects` | required | Lists accessible projects; `q` searches names, `page` selects a 1-based page |
| `GET` | `/projects/new` | required | Renders the create form |
| `POST` | `/projects` | required | Creates a project from `key`, `name` and optional `description` |
| `GET` | `/projects/{key}` | required | Redirects to `/projects/{key}/issues` |
| `GET` | `/projects/{key}/edit` | required | Owner-only edit form |
| `POST` | `/projects/{key}` | required | Owner-only name/description update |
| `GET` | `/projects/{key}/delete` | required | Owner-only delete confirmation |
| `POST` | `/projects/{key}/delete` | required | Owner-only cascade delete |

## Response Codes

| Code | Meaning |
| --- | --- |
| `200 OK` | Page rendered |
| `302 Found` | Project created or edited (redirects to its issue list via `/projects/{key}`); project deleted (redirects to `/projects`) |
| `403 Forbidden` | Member attempts an owner-only action, or CSRF token is missing/invalid |
| `404 Not Found` | No project with that key, or caller cannot access it |
| `422 Unprocessable Entity` | Validation failure; the form is re-rendered with errors and its input preserved |

## Behavioural Notes

- **Key format** — 2 to 10 characters, uppercase letters A–Z only. Lowercase input is rejected, not normalised, so what the user types is what is stored.
- **Key uniqueness** — Keys are unique. Two simultaneous creates with the same key result in one success and one validation failure ("key already in use"), never a server error.
- **Immutability** — The key and owner never change; the owner may edit name and description or delete the project with its dependent data.
- **Ordering and search** — Only owned or assigned-to projects appear, sorted by key ascending, 25 per page. `q` searches names case-insensitively; invalid `page` returns `422`.
- **Name and description** — Name is 3 to 100 characters after trimming and need not be unique; optional description is at most 1000 characters.

## Requirements

| ID | Category | Requirement |
| --- | --- | --- |
| `PRJ-1` | Basic | A valid key and name creates the project and redirects to `/projects/{key}/issues` |
| `PRJ-2` | Basic | `GET /projects` lists only owned or assigned-to projects (key and name), sorted by key ascending; see project access spec for search and paging |
| `PRJ-3` | Validation | A key that is not 2–10 characters of A–Z returns `422`, creates no project, and preserves the entered values |
| `PRJ-4` | Validation | A name shorter than 3 or longer than 100 characters after trimming returns `422` and creates no project |
| `PRJ-5` | Validation | A key that already exists returns `422` with a "key already in use" error; the existing project is not changed |
| `PRJ-6` | Concurrency | Two simultaneous creates with the same key yield exactly one project and one `422`; neither returns `5xx` |
| `PRJ-7` | Basic | `GET /projects/{key}` for an unknown key returns `404` |
| `PRJ-8` | Authentication | All endpoints return the unauthenticated responses defined in the authentication spec; no project data is disclosed |
| `PRJ-9` | Persistence | Created projects remain listed after an application restart |

## Out of Scope

- Archiving, ownership transfer, invitations, explicit membership tables or avatars. Editing, deletion, descriptions and derived membership are specified in [`project-access-spec.md`](./project-access-spec.md).
