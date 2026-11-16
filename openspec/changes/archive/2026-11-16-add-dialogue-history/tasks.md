# Tasks

Browser verification handoff: on 2026-11-16, the user requested to perform browser testing personally. Automated browser testing was stopped. The user subsequently reported that manual testing passed. Remaining browser acceptance is complete on that basis; it is not a claim that all planned automated browser checks ran. Existing screenshots and backend results are in `test-evidence/history/`. Do not resume delegated browser testing without a new user request.

## 1. In-memory storage and atomic retention

- [x] 1.1 Add managed Spring JDBC and H2 dependencies, memory-only datasource configuration, and startup schema/index resources; verify startup initializes the table without file-backed H2 or tracing.
- [x] 1.2 Implement history record mapping and ordered JSON-text serialization in a dedicated history service/repository; verify real-H2 round trips preserve UUID, UTC time, normalized scenario, Unicode topic/phrase, speakers, and line order, including duplicate topics.
- [x] 1.3 Implement insertion and newest-ten eviction under a singleton lock spanning transaction begin through commit; verify integration tests for 0/1/10/11/many saves, timestamp ties, concurrent saves/readers, and rollback when cleanup fails preserve exact retained IDs and never expose more than ten committed records.
- [x] 1.4 Verify memory lifetime by closing and restarting the application context with the same database name; assert the list is empty and previous UUIDs are missing, and inspect an isolated working directory for absent generated-dialogue database/trace files. Document memory lifetime, retention ordering, and future PostgreSQL locking needs in README.

## 2. Generation integration and read APIs

- [x] 2.1 Add backend structural validation and controller orchestration after provider generation, capturing completion time and saving once with the normalized scenario; verify tests reject incomplete output without saves and retain prompt-only content flexibility.
- [x] 2.2 Return additive `historySaved`/`historyId` via an HTTP response DTO separate from the AI structured DTO; verify controller tests cover committed saves, serialization/insert/cleanup/commit failures with HTTP 200 and intact dialogue, and provider failures with no save attempt. Remove full-response stdout logging and verify no dialogue-content persistence fallback is introduced.
- [x] 2.3 Add summary/detail read endpoints with UTC timestamps, UUID validation, non-success storage errors, and `Cache-Control: no-store`; verify MockMvc and real-H2 tests for list shape/order, empty list, duplicate-topic detail selection, malformed IDs (400), missing/evicted IDs (404), storage errors (5xx), and no provider calls during reads.
- [x] 2.4 Update README with endpoint examples and save-status behavior; verify examples match response contract tests and existing generation request/response fields remain supported.

## 3. Static browsing and warning UI

- [x] 3.1 Add History navigation and a separate accessible save-warning region to index.html; verify complete results still render in place on save failure, warning clears on the next valid generation, and existing blank-input/invalid-response/previous-result behavior remains intact.
- [x] 3.2 Build history.html with newest-first clickable topics, UUID links, loading/empty/error states, and Go back navigation; verify each state and duplicate-topic selection in the browser without pagination.
- [x] 3.3 Build dialogue.html with validated UUID selection, complete-content validation, safe text rendering, ordered speaker lines, loading/missing-selection/404/error states, and Go back/Home navigation; verify all states keep navigation available and never initiate generation.
- [x] 3.4 Document page navigation and intentional shutdown data loss in README and a concise history-page lifetime message; verify wording agrees with the API and in-memory behavior.
- [x] 3.5 Complete browser acceptance using the existing delegated Playwright MCP evidence and the user's reported successful manual test. The user explicitly replaced further automated browser testing with manual verification; screenshots and backend results remain in test-evidence/history/.

## 4. Integrated acceptance checks

- [x] 4.1 Run the Java 25 Maven test suite with mocked provider access; verify existing generation/service tests and new H2/API tests all pass without a real API key.
- [x] 4.2 Accept the integrated history flow using the completed real-backend Playwright checks and the user's successful manual test. Actual restart was performed; remaining planned automated verification was superseded by manual acceptance at the user's request.
- [x] 4.3 Record final verification: 33 Java tests passed, existing browser screenshots and backend report are in test-evidence/history/, strict OpenSpec validation passed, and the user reported manual testing OK. No additional automated browser checks are required after the user's handoff.
