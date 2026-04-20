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
package io.kairo.core.agent.snapshot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.kairo.api.agent.Agent;
import io.kairo.api.agent.AgentSnapshot;
import io.kairo.api.agent.AgentState;
import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/** Tests for {@link CheckpointManager}. */
class CheckpointManagerTest {

    private InMemorySnapshotStore store;
    private CheckpointManager manager;

    @BeforeEach
    void setUp() {
        store = new InMemorySnapshotStore();
        manager = new CheckpointManager(store);
    }

    private AgentSnapshot createSnapshot(String agentId, int iteration, List<Msg> history) {
        return new AgentSnapshot(
                agentId,
                "test-agent",
                AgentState.IDLE,
                iteration,
                1000L,
                history,
                Map.of("model", "gpt-4"),
                Instant.now());
    }

    /** Minimal Agent stub that returns a fixed snapshot. */
    private Agent stubAgent(AgentSnapshot snapshot) {
        return new Agent() {
            @Override
            public String id() {
                return snapshot.agentId();
            }

            @Override
            public String name() {
                return snapshot.agentName();
            }

            @Override
            public AgentState state() {
                return AgentState.IDLE;
            }

            @Override
            public Mono<Msg> call(Msg input) {
                return Mono.empty();
            }

            @Override
            public void interrupt() {}

            @Override
            public AgentSnapshot snapshot() {
                return snapshot;
            }
        };
    }

    @Nested
    @DisplayName("Constructor")
    class ConstructorTests {

        @Test
        @DisplayName("throws on null store")
        void throwsOnNullStore() {
            assertThatThrownBy(() -> new CheckpointManager(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("snapshotStore must not be null");
        }
    }

    @Nested
    @DisplayName("savepoint()")
    class SavepointTests {

        @Test
        @DisplayName("stores snapshot successfully")
        void storesSnapshotSuccessfully() {
            AgentSnapshot snap =
                    createSnapshot("agent-1", 3, List.of(Msg.of(MsgRole.USER, "hello")));
            Agent agent = stubAgent(snap);

            StepVerifier.create(manager.savepoint("cp-1", agent)).verifyComplete();

            // Verify it's retrievable
            StepVerifier.create(manager.rollback("cp-1"))
                    .assertNext(
                            loaded -> {
                                assertThat(loaded.agentId()).isEqualTo("agent-1");
                                assertThat(loaded.iteration()).isEqualTo(3);
                                assertThat(loaded.conversationHistory()).hasSize(1);
                            })
                    .verifyComplete();
        }

        @Test
        @DisplayName("same ID overwrites previous checkpoint")
        void sameIdOverwritesPrevious() {
            AgentSnapshot snap1 =
                    createSnapshot("agent-1", 1, List.of(Msg.of(MsgRole.USER, "first")));
            AgentSnapshot snap2 =
                    createSnapshot("agent-1", 5, List.of(Msg.of(MsgRole.USER, "second")));

            manager.savepoint("cp-overwrite", stubAgent(snap1)).block();
            manager.savepoint("cp-overwrite", stubAgent(snap2)).block();

            StepVerifier.create(manager.rollback("cp-overwrite"))
                    .assertNext(
                            loaded -> {
                                assertThat(loaded.iteration()).isEqualTo(5);
                                assertThat(loaded.conversationHistory().get(0).text())
                                        .isEqualTo("second");
                            })
                    .verifyComplete();
        }

        @Test
        @DisplayName("errors on null checkpoint ID")
        void errorsOnNullCheckpointId() {
            AgentSnapshot snap = createSnapshot("agent-1", 1, List.of());
            Agent agent = stubAgent(snap);

            StepVerifier.create(manager.savepoint(null, agent))
                    .expectError(IllegalArgumentException.class)
                    .verify();
        }

        @Test
        @DisplayName("errors on null agent")
        void errorsOnNullAgent() {
            StepVerifier.create(manager.savepoint("cp-1", null))
                    .expectError(IllegalArgumentException.class)
                    .verify();
        }
    }

    @Nested
    @DisplayName("rollback()")
    class RollbackTests {

        @Test
        @DisplayName("returns correct snapshot for checkpoint ID")
        void returnsCorrectSnapshot() {
            AgentSnapshot snap =
                    createSnapshot(
                            "agent-rollback",
                            7,
                            List.of(
                                    Msg.of(MsgRole.USER, "msg1"),
                                    Msg.of(MsgRole.ASSISTANT, "resp1")));
            manager.savepoint("cp-rollback", stubAgent(snap)).block();

            StepVerifier.create(manager.rollback("cp-rollback"))
                    .assertNext(
                            loaded -> {
                                assertThat(loaded.agentId()).isEqualTo("agent-rollback");
                                assertThat(loaded.iteration()).isEqualTo(7);
                                assertThat(loaded.conversationHistory()).hasSize(2);
                            })
                    .verifyComplete();
        }

        @Test
        @DisplayName("returns empty for non-existent checkpoint")
        void returnsEmptyForNonExistent() {
            StepVerifier.create(manager.rollback("does-not-exist")).verifyComplete();
        }

        @Test
        @DisplayName("errors on blank checkpoint ID")
        void errorsOnBlankCheckpointId() {
            StepVerifier.create(manager.rollback("  "))
                    .expectError(IllegalArgumentException.class)
                    .verify();
        }
    }

    @Nested
    @DisplayName("listCheckpoints()")
    class ListCheckpointsTests {

        @Test
        @DisplayName("returns all checkpoint IDs")
        void returnsAllCheckpointIds() {
            AgentSnapshot snap = createSnapshot("agent-1", 1, List.of());
            Agent agent = stubAgent(snap);

            manager.savepoint("alpha", agent).block();
            manager.savepoint("beta", agent).block();
            manager.savepoint("gamma", agent).block();

            StepVerifier.create(manager.listCheckpoints().collectList())
                    .assertNext(
                            ids ->
                                    assertThat(ids)
                                            .containsExactlyInAnyOrder("alpha", "beta", "gamma"))
                    .verifyComplete();
        }

        @Test
        @DisplayName("returns empty when no checkpoints exist")
        void returnsEmptyWhenNone() {
            StepVerifier.create(manager.listCheckpoints()).verifyComplete();
        }
    }

    @Nested
    @DisplayName("deleteCheckpoint()")
    class DeleteCheckpointTests {

        @Test
        @DisplayName("removes checkpoint so rollback returns empty")
        void removesCheckpoint() {
            AgentSnapshot snap = createSnapshot("agent-1", 1, List.of());
            manager.savepoint("to-delete", stubAgent(snap)).block();

            // Confirm it exists
            StepVerifier.create(manager.rollback("to-delete")).expectNextCount(1).verifyComplete();

            // Delete
            StepVerifier.create(manager.deleteCheckpoint("to-delete")).verifyComplete();

            // Confirm gone
            StepVerifier.create(manager.rollback("to-delete")).verifyComplete();
        }

        @Test
        @DisplayName("errors on null checkpoint ID")
        void errorsOnNullCheckpointId() {
            StepVerifier.create(manager.deleteCheckpoint(null))
                    .expectError(IllegalArgumentException.class)
                    .verify();
        }
    }

    @Nested
    @DisplayName("Integration: multiple savepoints maintain distinct state")
    class IntegrationTests {

        @Test
        @DisplayName("multiple savepoints capture distinct agent states")
        void multipleSavepointsDistinctState() {
            AgentSnapshot state1 =
                    createSnapshot("agent-1", 1, List.of(Msg.of(MsgRole.USER, "step1")));
            AgentSnapshot state2 =
                    createSnapshot(
                            "agent-1",
                            3,
                            List.of(
                                    Msg.of(MsgRole.USER, "step1"),
                                    Msg.of(MsgRole.ASSISTANT, "resp1"),
                                    Msg.of(MsgRole.USER, "step2")));
            AgentSnapshot state3 =
                    createSnapshot(
                            "agent-1",
                            5,
                            List.of(
                                    Msg.of(MsgRole.USER, "step1"),
                                    Msg.of(MsgRole.ASSISTANT, "resp1"),
                                    Msg.of(MsgRole.USER, "step2"),
                                    Msg.of(MsgRole.ASSISTANT, "resp2"),
                                    Msg.of(MsgRole.USER, "step3")));

            manager.savepoint("cp-1", stubAgent(state1)).block();
            manager.savepoint("cp-2", stubAgent(state2)).block();
            manager.savepoint("cp-3", stubAgent(state3)).block();

            // Rollback to cp-1 — earliest state
            StepVerifier.create(manager.rollback("cp-1"))
                    .assertNext(
                            snap -> {
                                assertThat(snap.iteration()).isEqualTo(1);
                                assertThat(snap.conversationHistory()).hasSize(1);
                            })
                    .verifyComplete();

            // Rollback to cp-2 — middle state
            StepVerifier.create(manager.rollback("cp-2"))
                    .assertNext(
                            snap -> {
                                assertThat(snap.iteration()).isEqualTo(3);
                                assertThat(snap.conversationHistory()).hasSize(3);
                            })
                    .verifyComplete();

            // Rollback to cp-3 — latest state
            StepVerifier.create(manager.rollback("cp-3"))
                    .assertNext(
                            snap -> {
                                assertThat(snap.iteration()).isEqualTo(5);
                                assertThat(snap.conversationHistory()).hasSize(5);
                            })
                    .verifyComplete();
        }

        @Test
        @DisplayName("savepoint and rollback full round-trip preserves all fields")
        void fullRoundTripPreservesAllFields() {
            List<Msg> history =
                    List.of(
                            Msg.of(MsgRole.USER, "What is the weather?"),
                            Msg.of(MsgRole.ASSISTANT, "Let me check..."));
            Map<String, Object> context = Map.of("model", "gpt-4", "temperature", 0.7);
            Instant now = Instant.now();

            AgentSnapshot original =
                    new AgentSnapshot(
                            "agent-full",
                            "Full Agent",
                            AgentState.RUNNING,
                            42,
                            5000L,
                            history,
                            context,
                            now);

            manager.savepoint("full-test", stubAgent(original)).block();

            StepVerifier.create(manager.rollback("full-test"))
                    .assertNext(
                            restored -> {
                                assertThat(restored.agentId()).isEqualTo("agent-full");
                                assertThat(restored.agentName()).isEqualTo("Full Agent");
                                assertThat(restored.state()).isEqualTo(AgentState.RUNNING);
                                assertThat(restored.iteration()).isEqualTo(42);
                                assertThat(restored.totalTokensUsed()).isEqualTo(5000L);
                                assertThat(restored.conversationHistory()).isEqualTo(history);
                                assertThat(restored.contextState()).isEqualTo(context);
                                assertThat(restored.createdAt()).isEqualTo(now);
                            })
                    .verifyComplete();
        }
    }
}
