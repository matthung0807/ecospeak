# Tasks

## 1. Database quota and calendar calculations

- [x] 1.1 Add the global lock row and independent request ledger to schema.sql with unique idempotency keys and period indexes; verify H2 initialization, key uniqueness, independent history removal, and fresh database reset in JDBC integration tests.
- [x] 1.2 Add validated quota configuration and an injectable Clock; verify default 2/10 limits, Asia/Taipei midnight and Monday boundaries, alternate valid settings, invalid startup settings, and admission periods across resets in unit tests. Document settings and restart reset behavior in project documentation.
- [x] 1.3 Implement atomic admission, usage snapshots, finalization, and idempotent release under the database lock; verify concurrent final-slot requests, active reservations, failed-attempt retries, attempt-token fencing, and cleanup retry after database recovery with H2-backed tests.

## 2. Generation and API integration

- [x] 2.1 Refactor HistoryService transaction ownership and generation orchestration so history insertion, retention, usage finalization, and response snapshot commit together; verify provider calls hold no lifecycle/database locks and rollback tests cover serialization, insert, retention, usage finalization, and commit failures without audio eviction.
- [x] 2.2 Implement optional Idempotency-Key validation, scenario binding, completed replay, pending duplicate responses, and fresh attempts after definitive failure; verify provider invocation counts, 400/409 responses, replay after quota exhaustion and history eviction, missing-key independent requests, and concurrent duplicate behavior in controller/integration tests.
- [x] 2.3 Add GET /api/dialogs/quota and structured 429 responses with no-store caching, timestamps, counts, exhausted periods, and Retry-After; verify daily-only, weekly-only, both-exhausted, reserved-only exhaustion, and 503 storage failures with controller tests. Document the API contracts and the change from unsaved success to save failure.
- [x] 2.4 Update DialogueControllerTests and relevant history tests for mandatory successful saving; verify successful response fields remain compatible, failed generation releases usage, and prior history/audio survive transaction rollback. Run targeted Maven tests for the affected classes.

## 3. Practice page integration

- [x] 3.1 Add quota loading, usage/reservation display, reset messaging, refresh on outcomes/focus/reset, and quota-read retry; verify loading, unavailable, daily exhausted, weekly exhausted, reset recovery, and stale multi-tab 429 behavior in browser testing.
- [x] 3.2 Add session-scoped unresolved keys, same-key transport retry and pending-duplicate recovery, a separate Recover pending result action usable at quota exhaustion, and new keys for explicit new generation or edited scenarios; verify lost-response recovery across reload without extra usage or provider calls.
- [x] 3.3 Remove unsaved-success warnings and integrate quota-aware error handling while preserving input, previous content, audio association, and navigation; verify saving failures and 429 preserve prior results and retained audio preparation/retry/playback still work at quota exhaustion.
## 4. Integration regression checks

- [x] 4.1 Run the relevant Maven backend suite and existing SpeechTests/history regressions; confirm concurrency, rollback, duplicate replay, retention independence, and audio behavior pass together.
- [x] 4.2 Verify restarting the app with unchanged in-memory H2 configuration clears both history and quota; review git diff to confirm no identity design or datasource configuration changes and retain backend/browser verification evidence.

  Verification note: `mvn -q test` and the separate `HistoryLifetimeTests` restart check passed. The user reported successful manual browser testing; no screenshots were provided. The datasource configuration is unchanged.
