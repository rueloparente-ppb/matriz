# Activity History

## Overview

Every issue keeps an append-only log of changes, shown on the issue detail page, newest first. An entry records who changed what, from which value to which, and when. The log is written by the system as a side effect of changes defined in [`issues-spec.md`](./issues-spec.md), [`board-spec.md`](./board-spec.md) and [`sprints-spec.md`](./sprints-spec.md); no endpoint creates entries directly. Authentication is required (see [`authentication-spec.md`](./authentication-spec.md)).

## HTTP API

| Method | Path | Description |
| --- | --- | --- |
| `GET` | `/projects/{key}/issues/{number}/history` | Returns the history list fragment; the detail page includes the same list |

## Response Codes

| Code | Meaning |
| --- | --- |
| `200 OK` | Fragment rendered |
| `404 Not Found` | Unknown project or issue |

## Behavioural Notes

- **Entry shape** — Each entry has actor (user), timestamp, a kind, and old and new display values. The kinds are `CREATED`, `FIELD_CHANGED` (with the field name) and `SPRINT_CHANGED`.
- **Recorded fields** — `title`, `description`, `type`, `priority`, `status`, `assignee`, and sprint membership.
- **Display values** — Assignee is recorded by display name, sprint by name, empty values shown as "None". Long values (description, title) are stored in full but displayed truncated to 100 characters.
- **One entry per field** — A single change of one field produces one entry. A change that does not alter the stored value produces none.
- **Atomic** — The change and its entry are stored together; a failed change leaves no entry and a failed entry write leaves no change.
- **Not recorded** — Comments and system timestamps.
- **Ordering** — Newest first; ties by creation order, newest first.
- **Stable names** — Entries keep the display name captured when written; later renames of a user or sprint do not alter old entries.

## Requirements

| ID | Category | Requirement |
| --- | --- | --- |
| `HST-1` | Basic | Creating an issue writes one `CREATED` entry with the creating user and time |
| `HST-2` | Basic | Changing a recorded field writes one entry with field name, old value, new value, actor and time |
| `HST-3` | Basic | Changing status from the board writes the same `status` entry as changing it from the issue page |
| `HST-4` | Basic | Setting a field to its current value writes no entry |
| `HST-5` | Validation | A rejected change (`422`) writes no entry |
| `HST-6` | Basic | Assigning, reassigning and clearing the assignee record display names, or "None" when empty |
| `HST-7` | Basic | Entries are listed newest first |
| `HST-8` | Basic | Entries for an issue are never shown on another issue |
| `HST-9` | Basic | Values over 100 characters are displayed truncated; the full value is not lost |
| `HST-10` | Basic | Comments produce no entries |
| `HST-11` | Security | Values containing HTML or script are rendered escaped |
| `HST-12` | Basic | Unknown project or issue returns `404` |
| `HST-13` | Persistence | Entries remain after an application restart; no endpoint edits or deletes them |
| `HST-14` | Authentication | Unauthenticated requests receive the responses defined in the authentication spec; no history is disclosed |

## Out of Scope

- Project-level or user-level activity feeds, filtering or paging of history, undo/revert, diffs of long text, email or other notification.
