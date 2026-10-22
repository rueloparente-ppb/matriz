# Comments — Commit-Driven Implementation Plan

**Spec:** [comments-spec.md](comments-spec.md)
**Test plan:** none — checks below are **planned verification** derived from the spec.

## Overview

Plain-text, immutable comments on the issue detail page, added through htmx. This is MR 7 in the global stack. It is independent of the board and could be reordered before it if needed.

**Estimated size:** ~150–220 source lines — single PR (base: board PR)

**Merge safety:** Additive table, endpoints and a section of the detail page.

## Tasks

### 1. Comment persistence and service

**Commit intent:** A comments table and a service that stores a comment with the server-side author and time on an issue resolved by project key and number.
**Dependencies:** issues group 2
**Estimated source lines:** ~60–90
**Requirements:** CMT-1, CMT-2, CMT-5, CMT-6, CMT-8, CMT-9

- [ ] 1.1 Flyway migration: comments (issue FK, author FK, body, created_at, identity id for tie order). Check: migration applies.
- [ ] 1.2 Service: resolve the issue (`404` first), validate, store; does not touch the issue's `updatedAt`. Check: tests for CMT-1, CMT-5, CMT-6 (posted author and time fields ignored), CMT-8.
- [ ] 1.3 List ordered by created_at, then id. Check: tests for CMT-2, CMT-9.

### 2. Comment fragment and form

**Commit intent:** A comment list fragment and an htmx form on the detail page; errors re-render the form with `422`.
**Dependencies:** 1
**Estimated source lines:** ~90–130
**Requirements:** CMT-3, CMT-4, CMT-7, CMT-10

- [ ] 2.1 `GET` and `POST` endpoints returning the fragment; validation (trimmed 1–2000) re-renders with the typed text kept. Check: tests for CMT-3, CMT-4.
- [ ] 2.2 Escaped body with preserved line breaks (CSS `white-space: pre-wrap`). Check: test for CMT-7.
- [ ] 2.3 Include the fragment on the detail page; unauthenticated access. Check: test for CMT-10; manual check in the browser.

## Coverage and ordering

| Requirement | Group |
| --- | --- |
| CMT-1, CMT-2, CMT-5, CMT-6, CMT-8, CMT-9 | 1 |
| CMT-3, CMT-4, CMT-7, CMT-10 | 2 |

The groups run in order 1 → 2. History's guarantee that comments write no entries (HST-10) is checked in activity-history group 2.
