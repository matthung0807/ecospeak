# Tasks

## 1. Persistence setup

- [x] 1.1 Add the Spring Data JPA dependency and configure it to use the existing H2 datasource and SQL-managed schema; verify application startup and schema tests.
- [x] 1.2 Map the three existing tables as scalar entities in the entity package and create JpaRepository interfaces in the repository package; verify H2 mappings preserve IDs, timestamps, text and nulls without relationship annotations.

## 2. History persistence

- [x] 2.1 Replace history JDBC CRUD with repository operations preserving field values, ordering and ten-record retention; verify history retrieval, ties, retention and lifetime tests.
- [x] 2.2 Move the save transaction to a proxied `@Transactional` collaborator with the existing propagation and isolation, keeping the lock through commit and notifications after commit; verify concurrent saves, rollback and eviction behavior in history integration tests.

## 3. Quota persistence

- [x] 3.1 Replace quota JDBC reads, counts and writes with derived methods or native SQL, preserving the lock row, conditional state transitions and affected-row checks; verify quota schema, calendar and integration tests including concurrent admission and idempotency.
- [x] 3.2 Replace quota transaction templates with proxied `@Transactional` boundaries and preserve cleanup timing, failed-release retry and atomic finalization with history; verify cleanup failure, stale attempt, rollback and successful replay cases.

## 4. Integration verification

- [x] 4.1 Adapt persistence-specific failure injection in affected tests while retaining behavioral assertions; run the history, quota, controller and audio tests and verify the full Maven test suite.
- [x] 4.2 Review production persistence code to confirm no direct JDBC CRUD, JPQL or relationship annotations remain, and that queries use derived methods or native SQL; verify existing APIs, business logic and schema remain unchanged.
