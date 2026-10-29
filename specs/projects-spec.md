# Projects

## Overview

A project groups issues and is identified by a short, unique, immutable key such as `PROJ`. Any authenticated user can create and view every project; there is no project membership. See [`authentication-spec.md`](./authentication-spec.md) for session rules.

## HTTP API

| Method | Path | Auth | Description |
| --- | --- | --- | --- |
| `GET` | `/projects` | required | Lists all projects |
| `GET` | `/projects/new` | required | Renders the create form |
| `POST` | `/projects` | required | Creates a project from `key` and `name` |
| `GET` | `/projects/{key}` | required | Redirects to `/projects/{key}/issues` |

## Response Codes

| Code | Meaning |
| --- | --- |
| `200 OK` | Page rendered |
| `302 Found` | Project created, redirects to the new project's issue list |
| `404 Not Found` | No project with that key |
| `422 Unprocessable Entity` | Validation failure; the form is re-rendered with errors and its input preserved |

## Behavioural Notes

- **Key format** — 2 to 10 characters, uppercase letters A–Z only. Lowercase input is rejected, not normalised, so what the user types is what is stored.
- **Key uniqueness** — Keys are unique. Two simultaneous creates with the same key result in one success and one validation failure ("key already in use"), never a server error.
- **Immutability** — Neither key nor name can be edited and projects cannot be deleted.
- **Ordering** — The list is sorted by key ascending.
- **Name** — 1 to 100 characters after trimming; the name is not required to be unique.

## Requirements

| ID | Category | Requirement |
| --- | --- | --- |
| `PRJ-1` | Basic | A valid key and name creates the project and redirects to `/projects/{key}/issues` |
| `PRJ-2` | Basic | `GET /projects` lists every project (key and name) sorted by key ascending, regardless of who created it |
| `PRJ-3` | Validation | A key that is not 2–10 characters of A–Z returns `422`, creates no project, and preserves the entered values |
| `PRJ-4` | Validation | A blank name, or one longer than 100 characters after trimming, returns `422` and creates no project |
| `PRJ-5` | Validation | A key that already exists returns `422` with a "key already in use" error; the existing project is not changed |
| `PRJ-6` | Concurrency | Two simultaneous creates with the same key yield exactly one project and one `422`; neither returns `5xx` |
| `PRJ-7` | Basic | `GET /projects/{key}` for an unknown key returns `404` |
| `PRJ-8` | Authentication | All endpoints return the unauthenticated responses defined in the authentication spec; no project data is disclosed |
| `PRJ-9` | Persistence | Created projects remain listed after an application restart |

## Out of Scope

- Editing, archiving or deleting projects; project membership; project descriptions or avatars.
