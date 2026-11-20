# Proposal

## Why

History and quota persistence currently use JDBC directly. Moving CRUD access to Spring Data JPA provides a consistent repository-based persistence layer while retaining the application's existing behavior.

## What Changes

- Replace production JDBC CRUD access for history and quota with Spring Data JPA repositories and entity classes.
- Use transactional annotations to preserve the existing transaction boundaries, locking, and rollback behavior.
- Use derived query methods or native SQL; do not use JPQL or JPA relationship annotations.
- Preserve existing schema, API behavior, business logic, and volatile database lifetime. Changes outside persistence and the transaction boundaries needed to preserve its behavior are out of scope.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

None. This is a behavior-preserving refactor; existing history and generation requirements remain in force.

## Impact

The Maven dependencies, persistence configuration, history and quota database access, transactional coordination, and related tests are affected. Existing clients and stored record formats remain unchanged.
