# Proposal

## Why

EcoSpeak currently allows unlimited dialogue generation. Bound personal usage to two dialogues per calendar day and ten per calendar week while keeping audio available for already generated dialogues.

## What Changes

- Introduce one global quota, with configurable limits defaulting to 2 daily and 10 weekly and timezone defaulting to Asia/Taipei. Days begin at midnight; weeks begin Monday at midnight.
- Store usage and reservations alongside history in the existing H2 database. Keep its current configuration: restarting the application resets history and usage together.
- Reserve allowance atomically before provider work, finalize usage with history in one transaction, and release reservations on failure. History eviction must not restore usage.
- Add idempotent generation requests, quota status, and HTTP 429 responses with remaining allowances, reset timestamps, and Retry-After.
- Display usage and reset information near generation controls and disable generation when either limit is exhausted. Audio preparation, retries, playback, and downloads consume no additional allowance.
- **BREAKING**: A history save failure becomes a non-success generation response instead of HTTP 200 with unsaved content, so saving and usage accounting succeed together.
- No user identity, authentication, separate persistence system, or database configuration changes.

## Capabilities

### New Capabilities

- `dialogue-generation-quota`: Global calendar quotas, transactional reservations, usage reporting, and idempotent generation.

### Modified Capabilities

- `dialogue-generation`: Successful generation requires atomic history saving and quota finalization.
- `dialogue-practice-page`: Show usage, submit idempotency keys, handle quota exhaustion, and replace the unsaved-success warning behavior.

## Impact

Changes affect DialogueController, generation orchestration, HistoryService transaction boundaries, schema.sql, quota configuration, and index.html. Existing H2/JDBC dependencies suffice. History retention remains ten records and audio APIs retain their existing behavior. Controller and database integration tests must change; UI implementation requires Playwright MCP testing by the playwright-tester subagent with screenshots under AGENTS.md.
