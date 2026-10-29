# Repository Engineering Guidelines

These standards apply to new and changed production code in a Java backend with an HTMX-based UI. Spring Boot and Spring Modulith rules apply only when those frameworks are adopted. This document is policy, not evidence that a subsystem or standard is already implemented.

Persistence, durable event publication, tracing, metrics/Actuator, health probes, and external-infrastructure testing rules apply when that subsystem exists or is introduced. Do not add infrastructure solely to satisfy a conditional rule.

## 1. Architecture & Package Structure

**Base package:** `com.example` below is illustrative only; use the project's actual base package.

### Module Structure

Each bounded context is a top-level package. Expose a root API package and hide implementation details in sub-packages.

```text
com.example.module_name
├── ModuleNameApi.java          (Public synchronous API interface)
├── SomethingHappenedEvent.java (Public asynchronous event contract)
├── package-info.java           (Optional: @ApplicationModule config)
├── config/                     (Internal configuration and property bindings)
├── core/                       (Internal entities, domain services, repositories)
└── web/                        (Internal controllers, DTOs, mappers)
```

- **HTMX placement:** HTMX belongs in a bounded context's internal `web/` adapter, not a separate business layer. Controllers translate HTTP/HTMX requests into module operations and render HTML; business rules remain in domain/application code.
- **Server-rendered UI:** The server owns browser-visible state. Prefer HTML documents and fragments over client state stores, duplicated business validation, or JSON-driven UI state for repository-owned browser flows.
- **View ownership:** Align templates and fragments with their owning context. Centralize shared layouts or presentation-only fragments only when they contain no business logic and expose no module internals.

## 2. Java Best Practices

Use the project's configured Java version. Apply the following language/API recommendations only when that version supports them; do not upgrade Java to satisfy this document.

- **Records:** Prefer records for immutable DTOs, events, and projections. Use classes when framework requirements, identity, lifecycle, or required mutability make records unsuitable. No Lombok on records.
- **Null safety:** Repository-owned APIs must not use `null` for expected absence. Use `Optional<T>` for return values representing absence, never as fields or method parameters. Framework implementations may use `null` only when their contracts require it.
- **Type inference:** Use `var` only when the right-hand-side type is immediately obvious.
- **Switch expressions:** Use exhaustive switch expressions with `->`, without fall-through.
- **Pattern matching:** Use pattern matching for `instanceof` instead of explicit casts.
- **Sealed types:** Use sealed interfaces/classes for finite, well-known hierarchies.
- **Text blocks:** Use text blocks for multiline SQL, JSON, or XML.
- **Concurrency:** Use virtual threads for I/O-bound concurrency when supported. Do not hold locks across blocking I/O. Keep critical sections small and choose synchronization according to the invariant being protected.
- **Collections:** Use `List.of()`, `Set.of()`, and `Map.of()` for immutable collections; use `List.copyOf()` at module boundaries. Prefer sequenced-collection methods such as `getFirst()`, `getLast()`, and `reversed()` when supported.

## 3. Inter-Module Communication

- **Synchronous:** Interact through root `ModuleNameApi` interfaces. Never import another module's `core/`, `web/`, or `config/` classes.
- **Asynchronous:** When using Spring, prefer Spring Events for notifications and secondary side effects that benefit from temporal decoupling. Use a public synchronous API when the caller needs a return value, immediate validation, or immediate consistency.
- **HTMX across modules:** HTMX is web transport, not an inter-module integration mechanism. Use the owning module's public API or established event flow; never shortcut through its internal controller, template, repository, or service.

### Event Ownership & Delivery

- Events belong to their publisher. Define event records in the publishing module's root package; consumers may import that public package.
- When the durable Spring Modulith Event Publication Registry is configured, use `@ApplicationModuleListener` for business events that must run after a successful publishing transaction and remain recoverable after processing failures. Configure the required transaction manager and registry.
- A transient process-local signal may use `@Async @EventListener` only when loss on restart is explicitly acceptable, no publishing transaction exists, and an integration test proves delivery. Document that exception in relevant architecture/risk documentation when available.
- Keep payloads small: primitive IDs or module-agnostic primitive types, not JPA entities or heavy internal DTOs.

### The Common Module

If a `common` module exists, it contains only genuine infrastructure cross-cutting concerns, not business logic: generic API envelopes, global exception handling such as Spring MVC `@ControllerAdvice`, and custom validation annotations.

## 4. JPA & Database

Apply these rules when JPA/database persistence exists or is introduced. Hibernate-specific annotations apply only when Hibernate is used and the configured version/dialect supports them.

- **Entities:** No `@Data` on entities. Use explicit accessors or Lombok `@Getter`/`@Setter` when Lombok is already used. Entity equality/hash codes must account for lifecycle, generated identifiers, transient instances, and persistence proxies. Prefer a stable natural business key only when one genuinely exists.
- **Enums:** Use `@Enumerated(EnumType.STRING)`. If database-native enums are adopted, use the established native mapping, such as `@JdbcTypeCode(SqlTypes.NAMED_ENUM)` when appropriate.
- **JSON:** Use `@JdbcTypeCode(SqlTypes.JSON)` for Hibernate JSON/JSONB mappings when supported by the dialect.
- **Auditing:** Use Hibernate `@CreationTimestamp` and `@UpdateTimestamp` for standard audit fields unless an established auditing mechanism owns them.
- **Migrations:** Use the established migration tool. For new relational persistence without an existing migration workflow, default to Flyway. With Spring Boot/Hibernate, use `spring.jpa.hibernate.ddl-auto=validate`; do not automatically mutate production schemas.
- **Event registry:** When durable Modulith publication is used, manage its schema through the migration tool and keep it compatible with the configured Modulith version. Do not infer or recreate framework-owned schema from this document.

## 5. Dependency Injection & Configuration

These annotations apply when Spring is used.

- Constructor injection only. Explicit constructors or Lombok `@RequiredArgsConstructor` are allowed for services/controllers when Lombok is used. No field `@Autowired` injection.
- Use `@ConfigurationProperties` with records where supported for type-safe property binding; avoid `@Value`.

## 6. Testing

Framework-specific helpers apply only to the adopted framework and supported versions.

- Use JUnit Jupiter and AssertJ (`assertThat(...)`), with Spring Boot-managed versions when applicable. Use Testcontainers for integration tests needing a real external service. Do not substitute H2 for a production database.
- Use test-managed `@Transactional` only when rollback accurately models isolation. Do not use it for behavior depending on commit, after-commit listeners, propagation, or durable publication.
- Use `@WebMvcTest` with `MockMvc` for focused Spring MVC/controller tests; use `@SpringBootTest` with `@AutoConfigureMockMvc` for full wiring.
- For HTMX-capable endpoints, cover ordinary requests and `HX-Request: true` when behavior differs. Assert views/fragments, rendered validation, statuses, and contractual response headers.
- Every URL exposed as a normal link or placed in browser history must load directly through a regular `GET` with complete page state for refresh/history restoration.
- Test HTMX mutations under the same authentication, authorization, CSRF, validation, and transaction rules as ordinary HTTP requests. Do not disable security to simplify tests.
- When adopting Spring Modulith, verify architecture with `ApplicationModules.of(Application.class).verify()` (substitute the actual application class).
- Use Modulith `PublishedEvents` to assert publication when available. Add Spring-context delivery tests for process-local async listeners; publication and direct-listener unit tests alone do not prove delivery. Test durable events across real transaction boundaries.
- When using a supported `@MockitoBean` version, mocks implementing multiple interfaces must cover all required interfaces to avoid wiring failures.
- Use deterministic local fixtures for external providers, clocks, and other nondeterministic dependencies.
- Test relevant concurrency, retry/idempotency, restart, security, and failure boundaries from the spec; a green suite alone does not establish acceptance coverage.

## 7. Observability & Logging

### Logging

- Use SLF4J with an explicit logger or Lombok `@Slf4j` when Lombok is used.
- Use parameterized logging, never string concatenation.
- Use the established configuration to emit structured JSON logs in production.
- Keep credentials and sensitive user content out of logs.

| Level | Usage |
| --- | --- |
| `ERROR` | System failures needing intervention; include an appropriate stack trace. |
| `WARN` | Recoverable errors or unexpected handled states. |
| `INFO` | Significant lifecycle events and business milestones. |
| `DEBUG` / `TRACE` | Developer diagnostics only. |

### Tracing

When tracing is enabled in Spring, use Micrometer Tracing and correlate `traceId`/`spanId` with structured logs through the configured integration. Do not duplicate framework MDC propagation without demonstrated need. Never put credential-bearing request targets in spans.

### Metrics & Health

- When Actuator/metrics are enabled, expose Prometheus through the established configuration, including `/actuator/prometheus` only when part of the deployment contract. Keep health, metrics, and admin access appropriately private.
- Use `@Timed` only when its instrumentation is configured and useful. Inject `MeterRegistry` for custom business metrics and bound tag values.
- Never tag by user ID, email, request ID, or raw exception message.
- Treat HTMX requests as ordinary HTTP requests for tracing/correlation. Do not create separate tracing paths or unbounded metric tags from `HX-Target`, `HX-Trigger`, raw URLs, or user-derived DOM identifiers.
- When container health probes are enabled, configure `/actuator/health/liveness` and `/actuator/health/readiness` according to the actual deployment contract. Health responses must not expose sensitive data.

## 8. General Change & HTMX Practices

- For fail-closed decisions, use explicit result types instead of nullable or implicit fallbacks; use sealed hierarchies when supported and appropriate.
- Reuse existing observability helpers with bounded tags rather than creating parallel emission paths.
- For concurrency, replacement, or failover changes, add regression tests with multiple concurrent consumers when continuity is contractual.
- Preserve required compatibility during phased API upgrades through explicit migration steps; use default-interface bridges only when they preserve intended behavior.
- Apply the same domain-approved normalization to state-key writes and lookups. Do not trim or transform identifiers requiring exact values.
- Preserve protocol-specific error semantics with explicit representations rather than collapsing them into generic failures.
- For cross-module state changes, use minimal public operations or the established event mechanism, never internal repositories/services.
- When a structured error contract exists, map domain/framework failures through one safe envelope and preserve its established fields.
- Prefer progressive enhancement: semantic links/forms with real `href`, `action`, and supported HTTP methods, then HTMX where useful without moving business state into JavaScript. Native forms support `GET`/`POST`; other mutations need an established method-override integration or working fallback.
- Keep HTTP semantics: `GET` is safe and read-only; state changes use mutation methods.
- Return the smallest fragment satisfying the declared `hx-target`/`hx-swap` contract. Keep target IDs stable and avoid broad DOM replacement when a local update suffices.
- Routes pushed/replaced into browser history must also render correctly through direct non-HTMX requests.
- Do not depend on HTMX response headers surviving a `3xx` redirect. Use normal redirects for ordinary requests; when an HTMX-specific redirect is needed, return an appropriate successful response with `HX-Location` or `HX-Redirect` for partial navigation or full reload respectively.
- Use `HX-Trigger` and browser events only for UI coordination, not as substitutes for Spring events, durable business events, or cross-module contracts.
- Use out-of-band swaps only for a small number of independent page regions, not hidden coupling between unrelated fragments/contexts.
- Keep custom JavaScript small and browser-focused. Do not introduce a SPA framework, client domain model, duplicated business validation, or client state store unless that architecture is explicitly adopted.
- Preserve CSRF/security for HTMX through the established server/template token integration; do not disable protection or invent parallel authentication.
- Keep service credentials server-side and out of source, images, browser content, logs, and fixtures.

## 9. Project Patterns

Add only concrete, recurring conventions supported by repository evidence; do not copy assumptions from another service.

## 10. Agent Instructions

- Make the smallest correct change; avoid unrelated refactoring, dependency changes, and scaffolding.
- Read applicable guidance, the relevant spec/contract, and existing code before making changes. Work only within the requested scope; planning documents do not authorize implementation.
- Do not invent build, test, lint, or format commands; use what the repository actually configures.
- Preserve architecture, module boundaries, and public contracts. Prefer immutable boundary data and simple, explicit code over clever abstractions.
- Add/update tests for behavior changes and schema migrations when the persistence workflow requires them. Format with the configured tools once tooling exists.
- For production browser changes, preserve server-owned state and prefer HTMX fragments over client state or a new JSON API solely for UI rendering. Inspect/reuse existing controller, template, header, validation, and security patterns before adding abstractions; keep HTMX concerns in `web/`.
- Verify fragment interaction and direct browser navigation for affected navigable routes. Do not claim progressive enhancement or history behavior works unless exercised.
- Preserve unrelated user changes. Inspect Git status/diffs before staging; do not reset, clean, stash, or rewrite work without explicit authorization. Commit/push only when requested for the current work.
- Registry pushes, deployments, public exposure, and data migrations/restores require explicit authorization.
- Keep communication concise, lead with the outcome, and state uncertainties plainly. Summarize changed files, checks actually run, and remaining limits; never claim unrun checks passed.
