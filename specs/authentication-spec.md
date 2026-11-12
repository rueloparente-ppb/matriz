# Authentication

## Overview

The application is a server-rendered web app. Every page and every htmx fragment endpoint, except the login page, the registration page and static assets, requires an authenticated session. Anyone can register an account with only a username and a password (entered twice); there is no email address, email verification or two-factor step. Seeded users also exist. There is no password reset or user management screen. Authentication uses a form login (username and password) and a server-side session cookie; a successful registration signs the new user in immediately. All state-changing requests require a valid CSRF token.

## HTTP API

| Method | Path | Auth | Description |
| --- | --- | --- | --- |
| `GET` | `/login` | none | Renders the login form |
| `GET` | `/register` | none | Renders the registration form |
| `POST` | `/register` | none | Creates an account from `username`, `password` and `confirmPassword` form fields and signs the user in |
| `POST` | `/login` | none | Authenticates with `username` and `password` form fields |
| `POST` | `/logout` | required | Ends the session |
| `GET` | `/` | required | Redirects to `/projects` |

## Response Codes

| Code | Meaning |
| --- | --- |
| `200 OK` | Login or registration form rendered |
| `302 Found` | Successful login redirects to the originally requested page, or `/` if none; unauthenticated browser request redirects to `/login`; successful registration redirects to `/`; logout redirects to `/login?logout` |
| `422 Unprocessable Entity` | Registration validation failure; the form is re-rendered with errors and the entered username preserved (the passwords are never echoed) |
| `401 Unauthorized` | Unauthenticated htmx request (`HX-Request: true`); the response carries `HX-Redirect: /login` |
| `403 Forbidden` | State-changing request with a missing or invalid CSRF token |

## Behavioural Notes

- **Uniform failure** — A wrong password and an unknown username produce the same response (redirect to `/login?error`, one generic message). The response does not reveal which part was wrong.
- **Registration** — The only inputs are username and password plus its confirmation. The display name is set to the username as entered. The new user has the same rights as every other user (see Authorisation).
- **Username rules** — A username is 3–50 characters of `A–Z a–z 0–9 . _ -`, trimmed of surrounding whitespace before validation and storage. Uniqueness is case-insensitive, so `Alice` and `alice` conflict.
- **Password rules** — A password is 8–72 characters (72 bytes is the bcrypt input limit) and is stored exactly as entered, never trimmed. The confirmation must equal it.
- **Registration errors** — A taken username is reported explicitly ("username already taken"), because registration has no other way to give useful feedback. Login failures stay uniform (see Uniform failure). When several rules fail at once, each failing field shows its own error and the status is still `422`.
- **Concurrent registration** — The database unique constraint on the lower-cased username decides the winner, so a lost race produces the same `422` as a sequential duplicate, never a `5xx`.
- **Authenticated visitors** — An already authenticated user requesting `/register` or `/login` is redirected to `/`.
- **Passwords** — Passwords are stored only as salted adaptive hashes (bcrypt); plain text never appears in storage, logs or responses.
- **Usernames** — Usernames are unique and compared case-insensitively. Each user also has a display name shown in assignee pickers, comments and history.
- **Authorisation** — There are no roles. Every authenticated user can read and change everything.
- **Identity boundary** — The login mechanism is replaceable. Other modules know only the current user as a `UserRef` (id, username, display name) obtained from `IdentityApi`; none sees credentials, `UserDetails`, or the authentication type. Everything that depends on form login (login page, password check, bcrypt) stays inside `identity` and the shared security configuration, so an external identity provider (for example Keycloak/OIDC) can replace it later without changing other modules.
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
| `AUTH-9` | Design | Code outside `identity` and the security configuration obtains the current user only through `IdentityApi` as a `UserRef`; `ApplicationModules.verify()` and an architecture test fail if another module imports Spring Security user types or `identity` internals |
| `AUTH-10` | Basic | `GET /register` without a session returns `200` with the registration form (username, password, confirm-password fields) and no email field |
| `AUTH-11` | Basic | A valid `POST /register` creates one user whose display name equals the username, creates a session, and returns `302` to `/` |
| `AUTH-12` | Persistence | The password of a registered user is stored only as a bcrypt hash; a later login with the same credentials after an application restart succeeds |
| `AUTH-13` | Validation | A username shorter than 3 or longer than 50 characters, or containing characters outside `A–Z a–z 0–9 . _ -`, returns `422`, creates no user and no session, and preserves the entered username |
| `AUTH-14` | Validation | A password shorter than 8 or longer than 72 characters returns `422`, creates no user and no session, and the response contains neither password |
| `AUTH-15` | Validation | A `confirmPassword` different from `password` returns `422` with a mismatch error, creates no user and no session |
| `AUTH-16` | Validation | A username that differs from an existing one (seeded or registered) only by case returns `422` with a "username already taken" error; the existing user and their password are unchanged and no session is created |
| `AUTH-17` | Concurrency | Two simultaneous registrations of the same username (any casing) yield exactly one user and one `422`; neither returns `5xx` |
| `AUTH-18` | Authentication | A `POST /register` without a valid CSRF token returns `403` and creates no user |
| `AUTH-19` | Basic | A registered user can log out and log in again with the same username in any casing (AUTH-5, AUTH-8) |
| `AUTH-20` | Basic | An authenticated request to `/register` or `/login` returns `302` to `/` |
| `AUTH-21` | Validation | A username with surrounding whitespace is trimmed and stored trimmed; a username that is blank after trimming returns `422` |

## Out of Scope

- Email, email verification, two-factor authentication, CAPTCHA, rate limiting of registration and login, password strength rules beyond length, password reset, password change, user management, roles and permissions, account lockout, remember-me, SSO. An external identity provider (Keycloak/OIDC) is out of scope for the MVP; AUTH-9 keeps the door open (see architecture, "Future identity provider").
