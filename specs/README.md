# Specs

Each feature has two files:

- **`<feature>-spec.md`**: the requirements. It owns the behaviour: routes, status codes, rules and numbered acceptance criteria (for example `PRJ-4`). When the code and a spec disagree, the spec wins, or it is updated in the same change.
- **`<feature>-tasks.md`**: the commit-driven implementation plan used to build the feature (commit groups, the requirement IDs each one covers, planned checks). These are historical delivery plans. Their checkboxes were not always ticked, so use the table below for status.

## Features

| Feature | Requirement IDs | Spec | Plan | Module(s) | Status |
| --- | --- | --- | --- | --- | --- |
| Authentication and registration | `AUTH` | [spec](authentication-spec.md) | [tasks](authentication-tasks.md) | `identity`, `common` | Implemented |
| Projects | `PRJ` | [spec](projects-spec.md) | [tasks](projects-tasks.md) | `projects` | Implemented |
| Issues | `ISS` | [spec](issues-spec.md) | [tasks](issues-tasks.md) | `issues` | Implemented |
| Kanban board | `BRD` | [spec](board-spec.md) | [tasks](board-tasks.md) | `board` | Implemented |
| Comments | `CMT` | [spec](comments-spec.md) | [tasks](comments-tasks.md) | `comments` | Implemented |
| Activity history | `HST` | [spec](activity-history-spec.md) | [tasks](activity-history-tasks.md) | `history` | Implemented |
| Sprints | `SPR` | [spec](sprints-spec.md) | [tasks](sprints-tasks.md) | `sprints` | Implemented |
| Project access, lifecycle and assignees | `ACC`, `ASG`, `PRJ-D`, `ISS-D` | [spec](project-access-spec.md) | [tasks](project-access-tasks.md) | `projects`, `issues` | Implemented |

**Precedence:** `project-access-spec.md` was written after the others. It adds ownership and membership, several assignees per issue, editing and deletion, and the dashboard. Where it overrides an earlier spec, that spec links to it.

## Reading order

1. [authentication](authentication-spec.md): sessions, CSRF and unauthenticated responses, which every other spec relies on.
2. [projects](projects-spec.md), then [project access](project-access-spec.md): who can see and change what.
3. [issues](issues-spec.md), [board](board-spec.md), [comments](comments-spec.md), [sprints](sprints-spec.md), [activity history](activity-history-spec.md).

## Adding or changing a feature

1. Write or update `<feature>-spec.md` with numbered acceptance criteria and an explicit out-of-scope list.
2. Write `<feature>-tasks.md`: small commit groups, each naming the requirement IDs it delivers and how it is checked.
3. Implement and test. Then update [`../docs/architecture.md`](../docs/architecture.md), [`../docs/database.md`](../docs/database.md) and [`../docs/api/openapi.yaml`](../docs/api/openapi.yaml) if modules, tables or routes changed, and add the feature to the table above.
