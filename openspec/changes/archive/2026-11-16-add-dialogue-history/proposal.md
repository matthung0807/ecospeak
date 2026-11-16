# Proposal

## Why

EcoSpeak currently replaces each generated conversation on the practice page, so users cannot revisit earlier practice material. A small, session-lifetime history enables repeat practice without another AI request.

## What Changes

- Save valid generated dialogues and their normalized input scenarios in an in-memory H2 database; retain only the newest 10 records with atomic insertion and eviction.
- Add History navigation, a topic list on `history.html`, and UUID-based saved content on `dialogue.html`, including navigation and loading/error/empty states.
- Preserve generation and rendering on `index.html`; report storage failures as warnings while returning and displaying the generated content.
- Add structural validation before persistence, without adding language-quality checks.
- Keep storage separate from AI generation and document a future PostgreSQL migration path; history intentionally disappears on shutdown.

## Capabilities

### New Capabilities

- `dialogue-history`: Volatile bounded storage, history retrieval, and saved-dialogue browsing.

### Modified Capabilities

- `dialogue-generation`: Persist valid results and return additive save-status metadata without turning storage failure into generation failure.
- `dialogue-practice-page`: Provide History navigation and a nonblocking save-failure warning.

## Impact

- Backend: generation controller orchestration, new history service/repository and read endpoints, response metadata, H2 and Spring JDBC/transaction dependencies, in-memory schema/configuration.
- Frontend: existing static index page and two new static pages using the existing plain HTML/JavaScript style.
- Verification: controller/service and real-H2 integration tests, plus delegated Playwright MCP testing with screenshots during implementation.
- Current source has no history storage or read APIs; server output is parsed but only the browser currently checks structural completeness. These are proposed additions, not baseline behavior.
- No authentication, pagination, file persistence, PostgreSQL integration, AI model changes, or new language-quality rules.
