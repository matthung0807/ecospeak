# Design

## Context

`HistoryRepository` and `QuotaService` issue JDBC statements against three H2 tables initialized by `schema.sql`. History saves use a synchronized writer lock around a new read-committed transaction, including insertion, retention and the quota-finalization callback; eviction listeners run after commit. Quota admission, snapshots and release serialize on a database lock row, and cleanup bookkeeping happens after their transactions. See the proposal for scope.

## Goals / Non-Goals

**Goals:** Preserve transaction propagation, commit timing, database locking, atomic updates, ordering, and error handling while replacing production CRUD access.

**Non-Goals:** Change schema semantics, API contracts, quota policy, history retention, audio lifecycle, or existing business decisions.

## Decisions

1. Add Spring Data JPA and map the existing history, quota request and lock tables as scalar entities in `com.matthung.ecospeak.entity`; put `JpaRepository` interfaces in `com.matthung.ecospeak.repository`. Preserve string UUIDs, timestamp precision, text fields and nullable values. Keep `schema.sql` authoritative and disable JPA schema generation. Unlike relationship mappings, scalar IDs preserve the existing independent tables and avoid implicit cascades.
2. Use derived repository methods and, where required for locking, conditional state transitions or aggregate counts, explicit native SQL. Do not use JPQL or relationship annotations, including one-to-one, one-to-many, many-to-one and many-to-many. Native conditional updates preserve the existing attempt-token and state guards and affected-row checks; flush and clear appropriately to prevent stale persistence-context reads. Retain database ordering by timestamp and canonical string ID, and ensure inserts are visible to retention queries.
3. Replace programmatic transaction boundaries with public methods on separately injected transactional collaborators so Spring proxy interception is effective. History's inner transaction remains `REQUIRES_NEW` and `READ_COMMITTED`, with the writer lock held until the proxied call returns after commit; the callback and quota finalization join that transaction. Notify eviction listeners only after successful commit. Admission, snapshot and release keep lock acquisition, cleanup and database work in their existing transaction, with cleanup-set removal only after successful commit. Release continues to defer failed cleanup. Do not extend transactions across provider calls. This preserves the current guarantees that a simplistic annotation on self-invoked methods would lose.
4. Preserve `SELECT ... FOR UPDATE` semantics on the singleton quota lock row using a native locking query in the active transaction. Do not replace it with an application-only lock: concurrent database transactions must remain serialized.

## Risks / Trade-offs

- JPA flush timing or bulk-update caching can change retention or state visibility → explicitly flush where needed and test rollback, state guards and read-after-write behavior.
- Proxy boundaries or transaction-manager changes can move commit outside the writer lock or break callback atomicity → use separate collaborators and integration tests for failures and concurrency.
- H2 type mapping or schema initialization may differ under JPA → retain the existing SQL schema and verify timestamps, text, identifiers, startup and shutdown behavior against H2.
- Existing JDBC-based tests inject failures at implementation-specific points → adapt those seams to repositories while retaining independent database assertions and behavioral coverage.

## Migration Plan

Update dependencies and mappings, migrate history and quota access and transaction boundaries, then run the relevant tests. The database is in-memory and initialized on startup, so no persistent-data migration is needed. Rollback consists of reverting the code and dependency change and restarting the application; both versions use the existing schema.
