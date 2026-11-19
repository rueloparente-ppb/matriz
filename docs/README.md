# Documentation

| Document | What it covers | Update it when |
| --- | --- | --- |
| [architecture.md](architecture.md) | System context, modules and their allowed dependencies, package layout, htmx conventions, key flows, architecture decisions (AD-n), testing strategy | A module, dependency, event, flow or decision changes |
| [database.md](database.md) | Entities, attributes, ER diagram, business rules mapped to constraints, Flyway migrations | A migration is added |
| [api/openapi.yaml](api/openapi.yaml) | HTTP endpoints for projects, issues and assignees: HTML pages and htmx fragments, not a JSON API | Routes, parameters or status codes change |
| [ui/design.md](ui/design.md) | Screens, navigation map, board wireframe checked against the specs | Screens or navigation change |
| [ui/board-wireframe.html](ui/board-wireframe.html) | Static low-fidelity board wireframe (open it in a browser) | — |

Other sources of truth:

- [`../specs/`](../specs/README.md): the required behaviour of each feature, plus its implementation plan.
- [`../AGENTS.md`](../AGENTS.md): engineering rules for code changes (module boundaries, Java, JPA, HTMX, testing).
