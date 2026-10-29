# Authentication

## Overview

The application is a server-rendered web app. Every page and every htmx fragment endpoint, except the login page and static assets, requires an authenticated session. Users are seeded into the database; there is no sign-up, password reset or user management screen. Authentication uses a form login (username and password) and a server-side session cookie. All state-changing requests require a valid CSRF token.

## HTTP API

| Method | Path | Auth | Description |
| --- | --- | --- | --- |
| `GET` | `/login` | none | Renders the login form |
| `POST` | `/login` | none | Authenticates with `username` and `password` form fields |
| `POST` | `/logout` | required | Ends the session |
| `GET` | `/` | required | Redirects to `/projects` |

## Response Codes

| Code | Meaning |
| --- | --- |
| `200 OK` | Login form rendered |
| `302 Found` | Successful login redirects to the originally requested page, or `/` if none; unauthenticated browser request redirects to `/login`; logout redirects to `/login?logout` |
| `401 Unauthorized` | Unauthenticated htmx request (`HX-Request: true`); the response carries `HX-Redirect: /login` |
| `403 Forbidden` | State-changing request with a missing or invalid CSRF token |

## Behavioural Notes

- **Uniform failure** — A wrong password and an unknown username produce the same response (redirect to `/login?error`, one generic message). The response does not reveal which part was wrong.
- **Passwords** — Passwords are stored only as salted adaptive hashes (bcrypt); plain text never appears in storage, logs or responses.
- **Usernames** — Usernames are unique and compared case-insensitively. Each user also has a display name shown in assignee pickers, comments and history.
- **Authorisation** — There are no roles. Every authenticated user can read and change everything.
- **htmx redirect** — A plain 302 would be followed by the browser inside the htmx swap, so htmx requests receive `401` plus `HX-Redirect`.

## Requirements

| ID | Category | Requirement |
| --- | --- | --- |
| `AUTH-1` | Basic | Valid credentials create a session and redirect to the originally requested page, or `/` if none |
| `AUTH-2` | Validation | Wrong password and unknown username both redirect to `/login?error` with the same message, and no session is created |
| `AUTH-3` | Authentication | An unauthenticated browser request to any page other than `/login` returns `302` to `/login`; no page content is returned |
| `AUTH-4` | Authentication | An unauthenticated htmx request returns `401` with `HX-Redirect: /login`; no fragment content is returned |
| `AUTH-5` | Basic | `POST /logout` invalidates the session; a later request with the old cookie is treated as unauthenticated |
| `AUTH-6` | Authentication | A state-changing request without a valid CSRF token returns `403` and changes no state |
| `AUTH-7` | Persistence | Seeded users can log in after an application restart; stored passwords are not the plain text password |
| `AUTH-8` | Basic | Usernames match case-insensitively at login |

## Out of Scope

- Sign-up, password reset, password change, user management, roles and permissions, account lockout, remember-me, SSO.
