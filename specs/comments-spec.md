# Comments

## Overview

Users add plain-text comments to an issue. Comments appear on the issue detail page, oldest first, and a new comment is appended with htmx without reloading the page. Authentication is required (see [`authentication-spec.md`](./authentication-spec.md)).

## HTTP API

| Method | Path | Description |
| --- | --- | --- |
| `GET` | `/projects/{key}/issues/{number}/comments` | Returns the comment list fragment |
| `POST` | `/projects/{key}/issues/{number}/comments` | Form field `body`; returns the updated comment list fragment |

## Response Codes

| Code | Meaning |
| --- | --- |
| `200 OK` | Fragment rendered, comment added |
| `404 Not Found` | Unknown project or issue |
| `422 Unprocessable Entity` | Invalid body; the fragment is re-rendered with the error and the typed text preserved |

## Behavioural Notes

- **Body** — 1 to 2000 characters after trimming. Whitespace-only is blank. Plain text, HTML-escaped on output, line breaks preserved.
- **Author and time** — Author is the current user and the time is set by the server; neither is accepted from the request.
- **Immutable** — Comments cannot be edited or deleted.
- **Not in history** — Adding a comment does not create an activity-history entry and does not change the issue's `updatedAt`.
- **Precedence** — Unknown project or issue returns `404` before the body is validated.

## Requirements

| ID | Category | Requirement |
| --- | --- | --- |
| `CMT-1` | Basic | A valid comment is stored with the current user as author and a server timestamp, and appears in the returned fragment |
| `CMT-2` | Basic | Comments are listed oldest first; ties are broken by creation order |
| `CMT-3` | Validation | A blank or whitespace-only body returns `422`, stores nothing and preserves the form |
| `CMT-4` | Validation | A body over 2000 characters after trimming returns `422` and stores nothing |
| `CMT-5` | Basic | Unknown project or issue returns `404`; a comment is never stored on another project's issue |
| `CMT-6` | Basic | Author and timestamp fields in the request are ignored |
| `CMT-7` | Security | A body containing HTML or script is rendered escaped; line breaks are preserved |
| `CMT-8` | Basic | Adding a comment leaves the issue's `updatedAt` and history unchanged |
| `CMT-9` | Persistence | Comments remain after an application restart |
| `CMT-10` | Authentication | Unauthenticated requests receive the responses defined in the authentication spec; no comment is stored or disclosed |

## Out of Scope

- Editing, deleting, replying/threading, mentions, notifications, Markdown, attachments.
