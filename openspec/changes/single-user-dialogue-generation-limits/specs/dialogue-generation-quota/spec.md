# Spec Delta

## Purpose

Bound single-user dialogue generation with global calendar quotas while preserving access to previously generated dialogue audio.

## ADDED Requirements

### Requirement: Apply global calendar quotas
The system SHALL allow a new dialogue only when both daily and weekly allowance remain. Defaults SHALL be 2 per day, 10 per week, and timezone Asia/Taipei; all three SHALL be configurable. Days SHALL start at local midnight and weeks SHALL start Monday at local midnight. Quotas SHALL apply globally without user identity.

#### Scenario: Daily exhaustion
- **GIVEN** two successful dialogues in the current day and weekly usage below ten
- **WHEN** another new dialogue is requested
- **THEN** the request is rejected before a provider call.

#### Scenario: Weekly exhaustion
- **GIVEN** ten successful dialogues in the current week and unused daily allowance
- **WHEN** another new dialogue is requested
- **THEN** the request is rejected before a provider call.

#### Scenario: Calendar boundaries
- **WHEN** local midnight begins a new day
- **THEN** that day's allowance becomes available subject to weekly usage
- **AND** weekly allowance resets only when Monday begins in the configured timezone.

### Requirement: Reserve and finalize allowance atomically
Accepted new generation SHALL reserve one daily and one weekly allowance before provider work. Active reservations SHALL reduce available allowance. History saving and usage finalization SHALL succeed atomically. Provider, validation, or saving failures SHALL release the reservation without creating a successful generation.

#### Scenario: Concurrent final slot
- **GIVEN** only one allowance remains under either applicable limit
- **WHEN** concurrent requests attempt new generation
- **THEN** at most one reserves that allowance and invokes the provider.

#### Scenario: Failed generation or save
- **WHEN** provider work, output validation, serialization, insertion, retention, or commit fails
- **THEN** no new history or committed usage remains and the reservation is released
- **AND** existing history and its audio remain intact.

#### Scenario: Generation crosses midnight
- **WHEN** an accepted generation completes in a later day or week
- **THEN** its usage is charged to the periods captured at reservation time.

### Requirement: Keep usage in the history database independently of retention
Usage and reservations SHALL use the existing H2 database without changing database configuration. Removing or evicting history SHALL NOT restore allowance. With the current in-memory database, application restart SHALL reset history, reservations, and usage together.

#### Scenario: History removal
- **WHEN** a counted dialogue is removed manually or by retention
- **THEN** its usage still counts for its original daily and weekly periods.

#### Scenario: Restart
- **WHEN** the application restarts using its current in-memory H2 configuration
- **THEN** history and all quota state are empty.

### Requirement: Deduplicate generation retries
Generation SHALL accept an optional Idempotency-Key header. A key SHALL bind to its normalized scenario for the database lifetime. Repeating a completed key with the same scenario SHALL replay its successful response without provider work or new usage, even after history eviction or quota exhaustion. Missing keys SHALL represent independent requests.

#### Scenario: Concurrent duplicate
- **WHEN** the same key and scenario arrive while generation is pending
- **THEN** only one provider operation runs and duplicates receive HTTP 409 with code generation_in_progress and Retry-After.

#### Scenario: Completed retry
- **WHEN** a completed key is retried with its original scenario
- **THEN** the original successful dialogue and history UUID are returned without additional usage
- **AND** replay does not recreate evicted history or audio.

#### Scenario: Reused key with changed scenario
- **WHEN** a key is submitted with a different normalized scenario
- **THEN** HTTP 409 with code idempotency_conflict is returned without provider work or usage changes.

#### Scenario: Retry after failure
- **WHEN** the original attempt has definitively failed and the same key and scenario are retried
- **THEN** a new attempt can reserve allowance subject to current limits and cannot overlap the failed attempt.

### Requirement: Report quota status and exhaustion
GET /api/dialogs/quota SHALL return daily and weekly limits, committed used counts, reserved counts, remaining allowances, reset timestamps, timezone, and nextAvailableAt. Exhausted generation SHALL return HTTP 429 with code dialogue_quota_exceeded, the same quota information, and Retry-After in seconds. nextAvailableAt SHALL be the later reset when both periods block generation.

#### Scenario: Inspect quota
- **WHEN** quota status is requested
- **THEN** current counts and nonnegative remaining allowances are returned without provider work or usage changes
- **AND** nextAvailableAt is null when allowance remains under both limits.

#### Scenario: Both limits exhausted
- **WHEN** both periods prevent a new dialogue
- **THEN** HTTP 429 identifies both exhausted periods and Retry-After reaches the later reset, rounded up to whole seconds.

#### Scenario: Storage unavailable
- **WHEN** quota storage cannot be read or reserved
- **THEN** generation returns HTTP 503 without invoking the provider and quota reads return a non-success status rather than fabricated allowance.

### Requirement: Include audio without extra dialogue usage
Audio preparation, retries, status, playback, and downloading for retained dialogues SHALL NOT consume dialogue allowance or be blocked by dialogue quota exhaustion. Failed audio SHALL NOT refund successful dialogue usage. Existing audio validation, capacity, and history-lifetime rules SHALL continue to apply.

#### Scenario: Exhausted quota with existing dialogue
- **WHEN** audio is prepared, retried, replayed, or downloaded for a retained dialogue after quota exhaustion
- **THEN** existing audio behavior remains available without changing usage.

#### Scenario: Text without audio
- **WHEN** a dialogue succeeds but audio is never requested or audio fails
- **THEN** exactly one dialogue allowance remains consumed.
