# ADR-011: DurableExecutionStore SPI Design

## Status
Accepted (v0.8)

## Context

Kairo agents execute multi-iteration ReAct loops that may be long-running (minutes to hours).
If the process crashes mid-execution, all progress is lost — the agent must restart from scratch.
There is no mechanism to persist execution state, replay events for recovery, or verify event
ordering integrity after a crash.

Additionally, tool calls during recovery may have side effects. Without an idempotency contract,
replaying a recovered execution can cause duplicate writes, duplicate API calls, or inconsistent
external state.

## Decision

### Event log schema

Introduce a `DurableExecutionStore` SPI in kairo-api (package `io.kairo.api.execution.durable`),
marked `@Experimental`, with the following core types:

- **`DurableExecution`** — aggregate root: `executionId`, `agentId`, `events` (ordered list),
  `checkpoint` (serialized snapshot), `status`, `version` (optimistic lock).
- **`ExecutionEvent`** — immutable event envelope: `eventType`, `timestamp`, `payload` (JSON),
  `hash` (integrity chain), `schemaVersion`.
- **`ExecutionStatus`** — enum: `RUNNING`, `PAUSED`, `COMPLETED`, `FAILED`, `RECOVERING`.

### Serialization format

JSON with Jackson for the v0.8 MVP. Protobuf is considered for v0.9 if benchmarks show
serialization as a bottleneck.

Canonical JSON serialization is required for hash determinism: sorted keys, no whitespace,
no trailing commas. This ensures identical payloads produce identical hashes regardless of
field insertion order.

### Hash chain formula

SHA-256 chain for event ordering verification during recovery:

```
hash_0 = SHA256("GENESIS" + canonical_json_payload)
hash_n = SHA256(hash_{n-1} + canonical_json_payload)
```

The hash chain is verified on recovery before replay. A broken chain indicates data corruption
or tampering — recovery aborts with `ExecutionCorruptedException`.

### Schema versioning

Each `ExecutionEvent` envelope carries a `schemaVersion` integer field.

- v0.8 readers MUST reject unknown versions (fail-fast with `UnsupportedSchemaVersionException`).
- Forward-compatibility strategy for v0.9: readers MAY ignore unknown fields but MUST NOT
  process unknown `schemaVersion` values.

### Concurrency model

Optimistic locking via the `version` column on `kairo_executions`:

- Every update includes `WHERE version = ?` in the SQL predicate.
- On conflict (version mismatch), the store throws `OptimisticLockException` and the caller retries.
- `kairo_execution_events` is append-only (INSERT only) — no locking needed on the events table.

### Recovery protocol

1. Find the latest `ITERATION_COMPLETE` event in the event log.
2. Verify the hash chain from genesis to that event.
3. Load the associated checkpoint snapshot.
4. Resume `ReActLoop` from that iteration index.

If no `ITERATION_COMPLETE` event exists, the execution restarts from the beginning.

### At-least-once idempotency contract

`ToolContext` carries an `idempotencyKey` derived from execution coordinates:

```
idempotencyKey = SHA256(executionId + ":" + iterationIndex + ":" + toolCallIndex)
```

Truncated to 32 hex characters for practical key length.

**Tool-side contract**: Tools must implement check-before-execute or upsert semantics
when using the idempotency key.

**Annotation-based opt-in/opt-out**:

- `@Idempotent` (at `io.kairo.api.tool.Idempotent`) — tool is safe to replay. Recovery
  re-executes the tool call with the same idempotency key.
- `@NonIdempotent` (at `io.kairo.api.tool.NonIdempotent`) — tool has side effects that
  cannot be safely replayed. Recovery skips execution and returns the cached result from
  the event log.

**Default safety policy**: Tools with UNKNOWN idempotency (no annotation) are treated as
non-idempotent. Recovery returns the cached result from the event log, or requests human
confirmation if no cached result exists. Opt-in to replay requires explicit `@Idempotent`.

### Store implementations

Two implementations ship with v0.8:

1. **`InMemoryDurableExecutionStore`** — in kairo-core, backed by `ConcurrentHashMap`.
   For unit/integration testing only.
2. **`JdbcDurableExecutionStore`** — in kairo-spring-boot-starter-core, backed by JDBC
   with Flyway-managed schema migration.

### JDBC schema

```sql
CREATE TABLE kairo_executions (
    execution_id VARCHAR(64) PRIMARY KEY,
    agent_id VARCHAR(128) NOT NULL,
    status VARCHAR(20) NOT NULL,
    version INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE kairo_execution_events (
    event_id VARCHAR(64) PRIMARY KEY,
    execution_id VARCHAR(64) NOT NULL REFERENCES kairo_executions(execution_id),
    event_type VARCHAR(50) NOT NULL,
    schema_version INT NOT NULL DEFAULT 1,
    payload_json TEXT NOT NULL,
    event_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_exec_events_exec_id ON kairo_execution_events(execution_id, created_at);
```

### Schema migration

Flyway manages all DDL. Migration file: `V1__create_execution_tables.sql`.
Spring Boot `spring.jpa.hibernate.ddl-auto` is NOT used — all schema changes go through
Flyway migrations exclusively.

## Consequences

### Positive

- Agents can survive process crashes and resume from the last completed iteration.
- Hash chain provides tamper-evident event log — corrupted events are detected before replay.
- Optimistic locking avoids pessimistic lock contention on the execution table.
- Default-safe idempotency policy protects against accidental side-effect replay.
- `InMemoryDurableExecutionStore` enables fast, deterministic testing without database setup.

### Trade-offs

- JSON serialization adds overhead compared to binary formats — acceptable for v0.8 MVP,
  Protobuf evaluation deferred to v0.9.
- Hash chain verification on recovery is O(n) in the number of events — acceptable for
  typical execution lengths (< 1000 events).
- Optimistic locking requires retry logic in callers — adds complexity to store consumers.
- Fail-fast on unknown schema versions means v0.8 stores cannot read v0.9 events —
  intentional to prevent silent data corruption.

## References

- ADR-010 (ToolResultBudget metadata as checkpoint input)
- `ReActLoop` in kairo-core
- `IterationGuards` in kairo-core
- `DefaultReActAgent` in kairo-core
