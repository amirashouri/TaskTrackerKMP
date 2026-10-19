---
name: kmp-room-database-migrations
description: Use when modifying Room schemas, DAOs, outbox queues, database builders, or transactions in the :packages:database or :packages:sync modules. Covers Context provisioning, split scenario testing, and migration verification.
related:
  - kmp-architecture-workflow
  - kmp-compose-tests
---

# KMP Room Database and Offline Architecture Workflow

The database engine is built on Room KMP and drives an offline-first outbox architecture. Multiplatform constraints are severe due to host execution limits (e.g., lack of `sqliteJni` on the Android library path).

## Room and Outbox Invariants
- **Atomic Transactions:** Any project or work-item modification must be written inside a single, unified database transaction (`Transactions.useWriterConnection`). The record write and its outbox queue row must be committed together. A two-step write is a critical crash vulnerability.
- **Pull Isolation:** Pull synchronization sync blocks (`LocalDataSyncSink`) must purely write incoming changes to the database. A pull action must **never** write to or enqueue rows into the outbox queue, as this creates infinite push loops.
- **Outbox Persistence:** During session teardown or logout sequences, local metadata and pull cursors may be cleared, but **outbox rows must be retained**. Deleting un-pushed outbox records discards unsaved user work.

## Test Boundaries and Context Injection
- **The Context Sandbox:** Room's `inMemoryDatabaseBuilder` will crash on execution if initialized without an explicit `Context`. Because a common unit test target cannot carry Robolectric runner annotations, split entry points must be used.
- **Split Testing Protocol:** Place database behavioral assertions in `commonTest` inside a baseline scenario object. Provide dedicated per-host entry points in `jvmTest` and `androidHostTest`. The Android entry point must carry `@RunWith(RobolectricTestRunner::class)`.
- **Environment Checks:** If a scenario initializes an outbox database queue that relies on native SQLite frameworks, gate the host invocation using `thisHostOpensRoomStores()`. If false, the platform lack of `sqliteJni` (like the Android host path) will cause a failure; the gate cleanly reports this target as a pass.
