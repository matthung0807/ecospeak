# Design

## Context

See `proposal.md` for motivation. The application uses Spring Boot 4.1.1, Java 25, static HTML and inline JavaScript. `DialogueController` calls `DialogueService` directly; the latter owns the OpenAI structured response request. The DTO contains topic, targetPhrase, and ordered dialogue entries. There is no database dependency or configuration. Browser validation already protects rendering; server parsing does not ensure nonblank fields. Existing controller tests use standalone MockMvc and mock the generation service.

The product proposal describes a small practice MVP; history expands that implemented baseline with bounded, temporary revisiting rather than a learning platform. Existing specs and provider prompt constraints remain applicable. Structural backend validation is new; it does not add semantic language evaluation.

## Goals / Non-Goals

**Goals:** Keep provider calls outside database transactions; separate generation, persistence, and HTTP response concerns; guarantee ten committed records under concurrent requests; preserve the generation response fields and home flow.

**Non-Goals:** ORM adoption, provider/model changes, durable storage, cross-process database writers, PostgreSQL deployment, account management, search, pagination, manual deletion, and automatic retries of save failures.

## Decisions

### Persistence through JDBC and a dedicated history service

Add Spring JDBC and runtime H2 dependencies using the project's dependency management. A dedicated history service and repository own storage, serialization, reads, and retention. Controller orchestration generates, validates, captures a UTC `Instant` immediately after valid generation, then attempts persistence. Keep the OpenAI DTO free of storage metadata so structured provider output remains unchanged. Return a separate response DTO with the original fields plus `historySaved` and nullable `historyId`.

Alternative: putting repository work in `DialogueService` would couple provider tests and AI concerns to storage. JPA adds mapping infrastructure for a small append/read/delete use case without a corresponding benefit.

### Schema and portable dialogue representation

Initialize the schema at application startup from a schema resource. Use table `dialogue_history`:

| Column | Type and constraints |
|---|---|
| `id` | `VARCHAR(36)` primary key; canonical lowercase UUID |
| `created_time` | `TIMESTAMP WITH TIME ZONE` not null; normalized to UTC |
| `topic` | `TEXT` not null |
| `target_phrase` | `TEXT` not null |
| `scenario` | `TEXT` not null; same normalized value sent to the provider |
| `dialogues` | `TEXT` not null; JSON array of speaker/text objects |

Use an index on `(created_time, id)` for ordering. Serialize with the application's configured JSON mapper and deserialize into ordered DTO entries. JSON in TEXT preserves Unicode, punctuation, and array order without relying on H2-specific JSON functions or a PostgreSQL JSONB contract. Validate the structure before saving and before rendering retrieved details. Do not enforce prompt-only properties such as exactly 4–5 lines. Alternatives: child rows add joins and position management; native JSON types introduce unnecessary migration differences. VARCHAR UUIDs make the specified canonical string tie-breaker explicit across databases.

### Atomic bounded writes

In this single-process deployment, use one singleton history writer lock surrounding a programmatic transaction, including its commit. Generate outside this lock. Inside the transaction, insert then select IDs ordered by `created_time DESC, id DESC`, and delete all IDs after position ten using parameterized statements. At only eleven transient rows this is simpler than vendor-specific delete/limit SQL. Acquire the lock before transaction creation and release only after transaction completion; do not rely on a synchronized method whose transaction interceptor could commit after releasing its monitor.

Use READ_COMMITTED reads so intermediate inserts/deletions are not exposed. Return save success only after commit. Any insert, cleanup, or commit failure rolls back and is caught outside the transaction boundary; existing records remain unchanged. Serialization failure occurs before the transaction. A timestamp tie uses canonical UUID descending, intentionally providing stable arbitrary order rather than promising completion order at sub-timestamp precision. A delayed save may immediately evict itself if ten newer records are already retained; successful saving means the transaction committed, not indefinite retention.

Alternative: an unconstrained transaction or count-then-delete allows races. A database mutex row would support multiple writers across processes but is unnecessary for localhost in-memory storage. Future multi-process PostgreSQL use must replace the process lock with database-wide serialization and rerun retention tests.

### Memory lifetime

Explicitly configure a `jdbc:h2:mem:` datasource, disable H2 file tracing and the console, and use no file-backed URL. Choose database lifetime tied to the application datasource, without `DB_CLOSE_DELAY=-1`, so closing the application context destroys history even if the JVM remains alive. Keep application-managed dialogue logging out of the persistence flow: remove the existing controller `System.out.println` of the complete response and log failures without generated content. No browser localStorage, sessionStorage, or file fallback is used for history.

### API contract and failure boundaries

- `POST /api/dialogs/generate` retains the existing request and top-level content fields. Valid output adds `historySaved: true, historyId: <uuid>` after commit; save failure returns HTTP 200 with `historySaved: false, historyId: null`. Invalid generated structure returns a server error without saving. Provider failures retain existing propagation.
- `GET /api/dialogs/history` returns summaries `[{id, createdTime, topic}]`, newest first, with UTC ISO-8601 timestamps; no rows returns `[]`.
- `GET /api/dialogs/history/{id}` returns `{id, createdTime, topic, targetPhrase, scenario, dialogue}` from storage. Reject malformed UUIDs with 400, unknown UUIDs with 404, and storage/decoding failures with a 5xx status. Static page requests themselves still load so the UI can explain missing selections.
- History responses use `Cache-Control: no-store` so refreshes observe eviction or a restarted application. No history read invokes the provider. Do not expose database exception details in responses.

Alternative: making the browser perform a second save request complicates navigation and allows callers to submit arbitrary history; wrapping all generation responses in a new envelope breaks current field access.

### Static pages and warnings

Follow the existing typography and simple layout. Add a History button and separate accessible warning region to the index. Clear the warning at the next valid generation start; only show the save warning after complete response validation and rendering. Preserve existing scenario, loading, duplicate-submit, generic-error, and previous-result behavior.

Create `history.html` and `dialogue.html` with ordinary static navigation, not browser back-stack dependence. History topics link to `dialogue.html?id=<encoded UUID>`; a refresh fetches the latest list. Detail parses and validates the parameter, loads by UUID, validates all content before rendering, and provides explicit navigation in all states. Missing/invalid selections, 404, other failures, and pending requests have separate messages. Use `textContent` for every stored/generated string, including topic links. No generated HTML is interpreted. Saved scenario is retained in storage/API but is not an additional required UI element.

## Risks / Trade-offs

- [History deliberately disappears on shutdown] → Document the lifetime in the UI/README; test restart and expired links.
- [Storage failure after costly generation] → Preserve the result, show the warning, and avoid silently retrying or duplicating saves.
- [Process lock limits future distributed deployments] → Encapsulate atomic retention in the repository/service boundary and require database locking for a future PostgreSQL rollout.
- [Default datasource/test lifetime can accidentally retain data] → Explicit memory-only configuration; isolate test databases and close/reopen the application with the same configured database name to verify destruction.
- [Browser mocks do not prove persistence] → Pair UI state tests with real-H2 integration and restart/file checks.

## Migration Plan

Deploy the new code and dependencies; startup initializes an empty memory schema. There is no existing history to migrate or backfill. Rollback restores previous code and removes the new memory datasource; no stored data recovery is required. A future PostgreSQL change will supply a driver, datasource, versioned schema and database-level concurrency control, retaining DTO/API contracts and JSON text representation if desired. It does not migrate these temporary H2 records automatically.

## Verification

Use mocked AI responses for deterministic backend and browser tests without provider costs. Real-H2 integration tests cover complete round trips, duplicate topics, timestamp ties, eleven-plus saves, concurrent saves with reads, and forced cleanup rollback. Controller tests cover metadata and status mapping; existing service tests continue proving provider request behavior. Test fresh application context restart with no history files in an isolated working directory. Delegate browser testing after implementation to the Playwright tester using Playwright MCP, capture screenshots, and repeat fixes/tests until requirements pass. Include real-backend generation-to-history navigation using a stub provider, plus intercepted browser responses for warning and retrieval failure branches; report which evidence uses mocks.
