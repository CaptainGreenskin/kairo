/*
 * Copyright 2025-2026 the Kairo authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.kairo.spring.execution;

import io.kairo.api.execution.DurableExecution;
import io.kairo.api.execution.DurableExecutionStore;
import io.kairo.api.execution.ExecutionEvent;
import io.kairo.api.execution.ExecutionEventType;
import io.kairo.api.execution.ExecutionStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * JDBC-backed implementation of {@link DurableExecutionStore} using Spring's {@link JdbcTemplate}.
 *
 * <p>All blocking JDBC calls are wrapped with {@link Schedulers#boundedElastic()} for reactive
 * compatibility. Uses optimistic locking on the {@code version} column for concurrent status
 * updates.
 *
 * @since v0.8
 */
public class JdbcDurableExecutionStore implements DurableExecutionStore {

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    public JdbcDurableExecutionStore(
            JdbcTemplate jdbcTemplate, TransactionTemplate transactionTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    public Mono<Void> persist(DurableExecution execution) {
        return Mono.fromCallable(
                        () -> {
                            transactionTemplate.executeWithoutResult(
                                    status -> {
                                        jdbcTemplate.update(
                                                "INSERT INTO kairo_executions"
                                                        + " (execution_id, agent_id, status,"
                                                        + " checkpoint, version, created_at,"
                                                        + " updated_at) VALUES (?, ?, ?, ?, ?, ?,"
                                                        + " ?)",
                                                execution.executionId(),
                                                execution.agentId(),
                                                execution.status().name(),
                                                execution.checkpoint(),
                                                execution.version(),
                                                Timestamp.from(execution.createdAt()),
                                                Timestamp.from(execution.updatedAt()));
                                        for (ExecutionEvent event : execution.events()) {
                                            insertEvent(execution.executionId(), event);
                                        }
                                    });
                            return (Void) null;
                        })
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorMap(
                        DuplicateKeyException.class,
                        e ->
                                new IllegalStateException(
                                        "Execution already exists: " + execution.executionId(), e))
                .then();
    }

    @Override
    public Mono<DurableExecution> recover(String executionId) {
        return Mono.fromCallable(
                        () -> {
                            List<DurableExecution> executions =
                                    jdbcTemplate.query(
                                            "SELECT execution_id, agent_id, status, checkpoint,"
                                                    + " version, created_at, updated_at FROM"
                                                    + " kairo_executions WHERE execution_id = ?",
                                            (rs, rowNum) -> mapExecution(rs),
                                            executionId);
                            if (executions.isEmpty()) {
                                return null;
                            }
                            DurableExecution base = executions.get(0);
                            List<ExecutionEvent> events =
                                    jdbcTemplate.query(
                                            "SELECT event_id, event_type, schema_version,"
                                                    + " payload_json, event_hash, created_at FROM"
                                                    + " kairo_execution_events WHERE execution_id = ?"
                                                    + " ORDER BY created_at",
                                            (rs, rowNum) -> mapEvent(rs),
                                            executionId);
                            return new DurableExecution(
                                    base.executionId(),
                                    base.agentId(),
                                    List.copyOf(events),
                                    base.checkpoint(),
                                    base.status(),
                                    base.version(),
                                    base.createdAt(),
                                    base.updatedAt());
                        })
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(exec -> exec != null ? Mono.just(exec) : Mono.empty());
    }

    @Override
    public Flux<DurableExecution> listPending() {
        return Mono.fromCallable(
                        () ->
                                jdbcTemplate.query(
                                        "SELECT execution_id, agent_id, status, checkpoint,"
                                                + " version, created_at, updated_at FROM"
                                                + " kairo_executions WHERE status IN (?, ?)",
                                        (rs, rowNum) -> mapExecution(rs),
                                        ExecutionStatus.RUNNING.name(),
                                        ExecutionStatus.RECOVERING.name()))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(Flux::fromIterable);
    }

    @Override
    public Mono<Void> appendEvent(String executionId, ExecutionEvent event) {
        return Mono.fromCallable(
                        () -> {
                            transactionTemplate.executeWithoutResult(
                                    status -> {
                                        insertEvent(executionId, event);
                                        jdbcTemplate.update(
                                                "UPDATE kairo_executions SET updated_at = ?"
                                                        + " WHERE execution_id = ?",
                                                Timestamp.from(Instant.now()),
                                                executionId);
                                    });
                            return (Void) null;
                        })
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    @Override
    public Mono<Void> updateStatus(
            String executionId, ExecutionStatus status, int expectedVersion) {
        return Mono.fromCallable(
                        () -> {
                            transactionTemplate.executeWithoutResult(
                                    txStatus -> {
                                        int rows =
                                                jdbcTemplate.update(
                                                        "UPDATE kairo_executions SET status"
                                                                + " = ?, version = version + 1,"
                                                                + " updated_at = ? WHERE"
                                                                + " execution_id = ? AND version"
                                                                + " = ?",
                                                        status.name(),
                                                        Timestamp.from(Instant.now()),
                                                        executionId,
                                                        expectedVersion);
                                        if (rows == 0) {
                                            throw new OptimisticLockException(
                                                    executionId, expectedVersion);
                                        }
                                    });
                            return (Void) null;
                        })
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    @Override
    public Mono<Void> delete(String executionId) {
        return Mono.fromCallable(
                        () -> {
                            jdbcTemplate.update(
                                    "DELETE FROM kairo_execution_events WHERE execution_id = ?",
                                    executionId);
                            jdbcTemplate.update(
                                    "DELETE FROM kairo_executions WHERE execution_id = ?",
                                    executionId);
                            return (Void) null;
                        })
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    private void insertEvent(String executionId, ExecutionEvent event) {
        jdbcTemplate.update(
                "INSERT INTO kairo_execution_events (event_id, execution_id, event_type,"
                        + " schema_version, payload_json, event_hash, created_at) VALUES (?, ?, ?,"
                        + " ?, ?, ?, ?)",
                event.eventId(),
                executionId,
                event.eventType().name(),
                event.schemaVersion(),
                event.payloadJson(),
                event.eventHash(),
                Timestamp.from(event.timestamp()));
    }

    private DurableExecution mapExecution(ResultSet rs) throws SQLException {
        return new DurableExecution(
                rs.getString("execution_id"),
                rs.getString("agent_id"),
                List.of(),
                rs.getString("checkpoint"),
                ExecutionStatus.valueOf(rs.getString("status")),
                rs.getInt("version"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant());
    }

    private ExecutionEvent mapEvent(ResultSet rs) throws SQLException {
        return new ExecutionEvent(
                rs.getString("event_id"),
                ExecutionEventType.valueOf(rs.getString("event_type")),
                rs.getTimestamp("created_at").toInstant(),
                rs.getString("payload_json"),
                rs.getString("event_hash"),
                rs.getInt("schema_version"));
    }
}
