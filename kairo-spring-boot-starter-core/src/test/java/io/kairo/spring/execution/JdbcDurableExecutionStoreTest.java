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

import static org.assertj.core.api.Assertions.assertThat;

import io.kairo.api.execution.DurableExecution;
import io.kairo.api.execution.ExecutionEvent;
import io.kairo.api.execution.ExecutionEventType;
import io.kairo.api.execution.ExecutionStatus;
import java.time.Instant;
import java.util.List;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import reactor.test.StepVerifier;

/** Tests for {@link JdbcDurableExecutionStore} backed by H2 + Flyway. */
class JdbcDurableExecutionStoreTest {

    private JdbcDurableExecutionStore store;
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        JdbcDataSource h2ds = new JdbcDataSource();
        h2ds.setURL("jdbc:h2:mem:testdb_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        h2ds.setUser("sa");
        DataSource ds = h2ds;
        Flyway flyway =
                Flyway.configure().dataSource(ds).locations("classpath:db/migration").load();
        flyway.migrate();
        jdbcTemplate = new JdbcTemplate(ds);
        TransactionTemplate transactionTemplate =
                new TransactionTemplate(new DataSourceTransactionManager(ds));
        store = new JdbcDurableExecutionStore(jdbcTemplate, transactionTemplate);
    }

    @Test
    void persistAndRecover() {
        DurableExecution exec = createExecution("exec-1", "agent-a", ExecutionStatus.RUNNING);
        StepVerifier.create(store.persist(exec)).verifyComplete();

        StepVerifier.create(store.recover("exec-1"))
                .assertNext(
                        recovered -> {
                            assertThat(recovered.executionId()).isEqualTo("exec-1");
                            assertThat(recovered.agentId()).isEqualTo("agent-a");
                            assertThat(recovered.status()).isEqualTo(ExecutionStatus.RUNNING);
                            assertThat(recovered.version()).isEqualTo(0);
                            assertThat(recovered.events()).isEmpty();
                        })
                .verifyComplete();
    }

    @Test
    void persistDuplicateErrors() {
        DurableExecution exec = createExecution("exec-dup", "agent-a", ExecutionStatus.RUNNING);
        StepVerifier.create(store.persist(exec)).verifyComplete();
        StepVerifier.create(store.persist(exec))
                .expectErrorMatches(
                        e ->
                                e instanceof IllegalStateException
                                        && e.getMessage().contains("already exists"))
                .verify();
    }

    @Test
    void recoverNonExistentReturnsEmpty() {
        StepVerifier.create(store.recover("no-such-id")).verifyComplete();
    }

    @Test
    void appendEventAndVerifyOrdering() {
        DurableExecution exec = createExecution("exec-ev", "agent-a", ExecutionStatus.RUNNING);
        StepVerifier.create(store.persist(exec)).verifyComplete();

        ExecutionEvent event1 = createEvent("ev-1", ExecutionEventType.MODEL_CALL_REQUEST);
        ExecutionEvent event2 = createEvent("ev-2", ExecutionEventType.MODEL_CALL_RESPONSE);

        StepVerifier.create(store.appendEvent("exec-ev", event1)).verifyComplete();
        StepVerifier.create(store.appendEvent("exec-ev", event2)).verifyComplete();

        StepVerifier.create(store.recover("exec-ev"))
                .assertNext(
                        recovered -> {
                            assertThat(recovered.events()).hasSize(2);
                            assertThat(recovered.events().get(0).eventId()).isEqualTo("ev-1");
                            assertThat(recovered.events().get(0).eventType())
                                    .isEqualTo(ExecutionEventType.MODEL_CALL_REQUEST);
                            assertThat(recovered.events().get(1).eventId()).isEqualTo("ev-2");
                            assertThat(recovered.events().get(1).eventType())
                                    .isEqualTo(ExecutionEventType.MODEL_CALL_RESPONSE);
                        })
                .verifyComplete();
    }

    @Test
    void updateStatusWithCorrectVersion() {
        DurableExecution exec = createExecution("exec-st", "agent-a", ExecutionStatus.RUNNING);
        StepVerifier.create(store.persist(exec)).verifyComplete();

        StepVerifier.create(store.updateStatus("exec-st", ExecutionStatus.COMPLETED, 0))
                .verifyComplete();

        StepVerifier.create(store.recover("exec-st"))
                .assertNext(
                        recovered -> {
                            assertThat(recovered.status()).isEqualTo(ExecutionStatus.COMPLETED);
                            assertThat(recovered.version()).isEqualTo(1);
                        })
                .verifyComplete();
    }

    @Test
    void updateStatusWithWrongVersionErrors() {
        DurableExecution exec = createExecution("exec-ver", "agent-a", ExecutionStatus.RUNNING);
        StepVerifier.create(store.persist(exec)).verifyComplete();

        StepVerifier.create(store.updateStatus("exec-ver", ExecutionStatus.COMPLETED, 99))
                .expectErrorMatches(e -> e instanceof OptimisticLockException)
                .verify();
    }

    @Test
    void listPendingReturnsOnlyRunningAndRecovering() {
        StepVerifier.create(
                        store.persist(createExecution("e-run", "agent-a", ExecutionStatus.RUNNING)))
                .verifyComplete();
        StepVerifier.create(
                        store.persist(
                                createExecution("e-rec", "agent-a", ExecutionStatus.RECOVERING)))
                .verifyComplete();
        StepVerifier.create(
                        store.persist(
                                createExecution("e-done", "agent-a", ExecutionStatus.COMPLETED)))
                .verifyComplete();
        StepVerifier.create(
                        store.persist(createExecution("e-fail", "agent-a", ExecutionStatus.FAILED)))
                .verifyComplete();
        StepVerifier.create(
                        store.persist(
                                createExecution("e-pause", "agent-a", ExecutionStatus.PAUSED)))
                .verifyComplete();

        StepVerifier.create(store.listPending().collectList())
                .assertNext(
                        list -> {
                            assertThat(list).hasSize(2);
                            assertThat(list)
                                    .extracting(DurableExecution::executionId)
                                    .containsExactlyInAnyOrder("e-run", "e-rec");
                        })
                .verifyComplete();
    }

    @Test
    void deleteRemovesExecutionAndEvents() {
        DurableExecution exec = createExecution("exec-del", "agent-a", ExecutionStatus.RUNNING);
        StepVerifier.create(store.persist(exec)).verifyComplete();

        ExecutionEvent event = createEvent("ev-del", ExecutionEventType.TOOL_CALL_REQUEST);
        StepVerifier.create(store.appendEvent("exec-del", event)).verifyComplete();

        StepVerifier.create(store.delete("exec-del")).verifyComplete();
        StepVerifier.create(store.recover("exec-del")).verifyComplete();
    }

    @Test
    void deleteNonExistentIsNoOp() {
        StepVerifier.create(store.delete("ghost")).verifyComplete();
    }

    private static DurableExecution createExecution(
            String id, String agentId, ExecutionStatus status) {
        Instant now = Instant.now();
        return new DurableExecution(id, agentId, List.of(), null, status, 0, now, now);
    }

    private static ExecutionEvent createEvent(String eventId, ExecutionEventType type) {
        return new ExecutionEvent(
                eventId, type, Instant.now(), "{\"key\":\"value\"}", "hash-" + eventId, 1);
    }
}
