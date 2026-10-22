# Kanban Board

## Overview

The board shows one project's issues as cards in three columns, one per status: To Do, In Progress, Done. Dragging a card to another column changes the issue's status. Authentication is required (see [`authentication-spec.md`](./authentication-spec.md)). The status rules live in [`issues-spec.md`](./issues-spec.md); the board is another way to apply them.

## HTTP API

| Method | Path | Description |
| --- | --- | --- |
| `GET` | `/projects/{key}/board` | Renders the board. Optional query `sprint` (sprint id or `none`), see [`sprints-spec.md`](./sprints-spec.md) |
| `POST` | `/projects/{key}/board/move` | Form fields `issue` (number) and `status`; returns the refreshed board fragment |

## Response Codes

| Code | Meaning |
| --- | --- |
| `200 OK` | Board or fragment rendered, move applied (or no-op) |
| `404 Not Found` | Unknown project, or issue number not in this project |
| `422 Unprocessable Entity` | `status` is not one of the three statuses, or `issue` is not a number |

## Behavioural Notes

- **Columns** — Exactly three columns are always shown, even when empty, titled in the fixed order To Do, In Progress, Done.
- **Card order** — Cards within a column are ordered by issue number ascending. Dragging does not store a position within a column; only the column change is persisted.
- **Card content** — Key, title, type, priority and assignee display name (or "Unassigned"). A card links to the issue detail page.
- **Failed move** — When a move is rejected the board is re-rendered from stored data, so the card visibly returns to its original column.
- **Same-column drop** — Dropping a card on its own column is a no-op: no change, no history entry.
- **Precedence** — Unknown project returns `404` before the issue or status are checked; unknown issue `404` before invalid status `422`.

## Requirements

| ID | Category | Requirement |
| --- | --- | --- |
| `BRD-1` | Basic | The board renders three columns in the order To Do, In Progress, Done, each containing only that project's issues with that status |
| `BRD-2` | Basic | Empty columns are rendered |
| `BRD-3` | Basic | Cards in a column are ordered by issue number ascending |
| `BRD-4` | Basic | A valid move persists the new status and returns the refreshed board fragment; the issue appears in the new column and not the old one |
| `BRD-5` | Basic | A status set by a move is the same value seen on the issue detail page and list |
| `BRD-6` | Validation | An invalid `status` or non-numeric `issue` returns `422` and changes nothing |
| `BRD-7` | Validation | An issue number that does not exist in this project returns `404`; an issue of another project is never moved |
| `BRD-8` | Basic | A move to the issue's current status changes nothing, including `updatedAt`, and records no history |
| `BRD-9` | Basic | A move is recorded in history as a status change (see the history spec) |
| `BRD-10` | Basic | Unknown project returns `404` |
| `BRD-11` | Authentication | Unauthenticated requests receive the responses defined in the authentication spec; no board data is disclosed |

## Out of Scope

- Manual ordering within a column, swimlanes, WIP limits, additional or renamed columns, board-level filters other than sprint, real-time updates for other viewers.
