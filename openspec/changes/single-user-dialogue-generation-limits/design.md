# Design

## Context

See proposal.md for motivation. DialogueController currently generates text and treats history saving as best effort. HistoryService uses a REQUIRES_NEW transaction under a lifecycle lock, commits insert and ten-record retention together, then notifies audio eviction listeners. AudioService uses that lifecycle lock for short state operations. H2 is in-memory and schema.sql initializes it; neither its URL nor history retention changes. Controller tests explicitly expect unsaved successful content today, so those expectations must change. Existing specs and product intent describe practice and reusable audio; the new behavior belongs in change deltas, not baseline edits.

## Goals / Non-Goals

**Goals:** Keep database transactions short, serialize quota admission across concurrent requests, atomically commit history and usage, and preserve post-commit audio cleanup. Make time-bound behavior deterministic with an injected Clock.

**Non-Goals:** Accounts, identity columns, authentication, new database infrastructure, durable storage across restart, new history deletion endpoints, and audio provider or playback redesign.

## Decisions

### One database ledger with a singleton admission lock

Add a seeded global lock row and a generation-request ledger in schema.sql. Each ledger row contains a server request UUID, optional unique idempotency key, normalized scenario, reservation instant, captured daily and Monday weekly period starts, attempt token, state (reserved/succeeded/failed), history UUID, and successful response snapshot. Use the same datasource and transaction manager as history. Usage queries count succeeded rows plus active reservations in each period. Retain ledger rows for the database lifetime independently of history, without cascading deletion. The response snapshot permits duplicate replay after history eviction without recreating expired history.

Use SELECT FOR UPDATE on the singleton row during short admission/finalization/release transactions. Check an existing key before quota exhaustion; bind it to the normalized scenario. A new request without a key gets its own request UUID. A completed key replays its snapshot, an active duplicate returns 409 generation_in_progress with Retry-After: 1, a mismatched scenario returns 409 idempotency_conflict, and a definitively failed key can acquire a new attempt token. Validate a supplied header as a nonblank string of at most 128 characters; malformed values return 400 before admission.

An in-process counter was rejected because the database must own usage. Counting history rows was rejected because ten-record eviction would restore allowance. Keeping only a history UUID was rejected because duplicate responses could not be replayed after eviction.

### Admission periods determine charging

Capture one instant using Clock and derive calendar starts in the configured ZoneId. Associate usage with those admission periods even if work completes after midnight. Remaining is max(0, limit - succeeded - reserved). Use calendar ZonedDateTime arithmetic for reset instants, including zones with daylight saving. Validate positive limits and a valid timezone at startup. Configuration uses ecospeak.quota.daily-limit=2, weekly-limit=10, and timezone=Asia/Taipei; no datasource changes.

Completion-time charging was rejected because a request admitted just before reset could exceed the next period's allowance. Fixed elapsed 24-hour and seven-day windows would not match calendar resets.

### Short transactions surrounding provider work

1. Validate input and idempotency header, then atomically replay or reserve under the database lock.
2. Invoke DialogueService and validate output outside database transactions and the history lifecycle lock.
3. Acquire the history lifecycle lock, then database admission lock, and in one transaction insert history, trim retention, finalize the matching attempt token, and store its successful response snapshot.
4. Commit before notifying audio eviction listeners. Build the successful GenerationResponse only after commit.
5. On provider, validation, serialization, or transaction failure, release the matching reservation in a separate transaction and return non-success. Release is idempotent; delayed callbacks cannot release or finalize a newer attempt.

Refactor HistoryService's transaction entry so quota-aware saving owns a single transaction instead of nesting its existing REQUIRES_NEW save. Preserve existing standalone save behavior for internal callers/tests if needed through a shared transactional save operation. Keep a consistent lock order: history lifecycle lock before database lock for combined operations; quota-only operations never acquire the history lock. Never notify audio eviction on rollback. A simple controller wrapper around current save was rejected because its separate commit could leave history without finalized usage.

Do not expire reservations while their synchronous provider operation can still complete. Cleanup runs in a finally path for definitive failures; if release fails because storage is down, retain an in-memory retry entry and retry release after recovery before admitting new work. Retaining allowance temporarily is preferable to allowing duplicate work. A full process restart clears all state under the existing database configuration; durable-database crash recovery is outside scope.

### API and UI contracts

Add GET /api/dialogs/quota, returning timezone, daily and weekly objects with limit, used, reserved, remaining, resetsAt, plus nextAvailableAt. Timestamps are ISO-8601 instants. When allowance exists nextAvailableAt is null; otherwise it is the latest reset among exhausted periods. HTTP 429 returns code dialogue_quota_exceeded and a quota object using the same shape, plus Retry-After rounded up to positive whole seconds. Quota responses use Cache-Control: no-store. Storage admission/read failures return 503; successful generation retains current fields and always reports historySaved true. Existing provider failure propagation remains unchanged after reservation cleanup.

index.html loads and refreshes quota, shows committed usage and reservations separately, and enables generation only with confirmed allowance. Refresh after outcomes, on visibility regain, and at nextAvailableAt; show a retry action on status failure. Handle 429 before generic errors and 409 generation_in_progress as pending retry. Preserve visible dialogue and audio on errors. Persist an unresolved key and its scenario in sessionStorage for transport retry across reload, clear it after definitive resolution, and create a new key on explicit new generation or scenario edit. Remove the unsaved-success warning. Existing audio calls bypass quota admission entirely.

## Risks / Trade-offs

An unresolved request gets a separate Recover pending result action, available even when quota disables new generation. This action reuses its key and never silently creates a new logical request. If the original attempt failed, its retry remains subject to server quota admission. Retry pending duplicates at the server-provided interval without consulting new-generation button availability.

- [Save failure no longer returns new text] → Document the breaking contract, preserve previous visible content, and test rollback plus released allowance.
- [Failed provider work may still incur cost] → Quotas bound successful dialogue allowance; existing provider behavior remains, and failures do not consume it.
- [Reservations temporarily block allowance during storage failure] → Retry cleanup; fail admission closed rather than inventing availability.
- [Response snapshots duplicate retained content] → Keep them only in the existing database lifetime; this supports idempotency after history eviction and creates no files.
- [Quota status can become stale across tabs] → Server admission remains authoritative; refresh UI on focus and every outcome and handle 429 explicitly.
- [Replayed UUID has been evicted] → Return the original response without restoring history; existing audio-not-found behavior explains unavailable audio.

## Migration Plan

Implement schema additions, quota orchestration and transaction refactoring, then API/UI integration. Existing schema initialization starts with zero quota usage under the unchanged in-memory configuration. Run targeted controller, service, H2 concurrency/rollback, and audio regression tests. Delegate UI verification to the playwright-tester subagent using Playwright MCP, capture screenshots, fix failures, and repeat until required tests pass. Rollback restores the prior application version; restart clears in-memory history and quota state as it does today.
