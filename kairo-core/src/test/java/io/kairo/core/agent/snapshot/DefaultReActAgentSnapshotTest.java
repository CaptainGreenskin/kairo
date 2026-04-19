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
import io.kairo.api.agent.SnapshotStore;
import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import io.kairo.api.model.ModelConfig;
import io.kairo.api.model.ModelProvider;
import io.kairo.api.model.ModelResponse;
import io.kairo.core.agent.AgentBuilder;
import io.kairo.core.agent.DefaultReActAgent;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** E2E tests for agent snapshot → restore → continue flow. */
class DefaultReActAgentSnapshotTest {

    @Nested
    @DisplayName("AgentSnapshot record")
    class SnapshotRecord {

        @Test
        @DisplayName("compact constructor provides defaults for nullable fields")
        void compactConstructorDefaults() {
            AgentSnapshot snapshot =
                    new AgentSnapshot("id", "name", AgentState.IDLE, 0, 0L, null, null, null);

            assertThat(snapshot.conversationHistory()).isEmpty();
            assertThat(snapshot.contextState()).isEmpty();
            assertThat(snapshot.createdAt()).isNotNull();
        }

        @Test
        @DisplayName("snapshot preserves all fields")
        void preservesFields() {
            List<Msg> history = List.of(Msg.of(MsgRole.USER, "hello"));
            Instant now = Instant.now();

            AgentSnapshot snapshot =
                    new AgentSnapshot(
                            "id",
                            "name",
                            AgentState.COMPLETED,
                            10,
                            5000L,
                            history,
                            Map.of("k", "v"),
                            now);

            assertThat(snapshot.agentId()).isEqualTo("id");
            assertThat(snapshot.agentName()).isEqualTo("name");
            assertThat(snapshot.state()).isEqualTo(AgentState.COMPLETED);
            assertThat(snapshot.iteration()).isEqualTo(10);
            assertThat(snapshot.totalTokensUsed()).isEqualTo(5000L);
            assertThat(snapshot.conversationHistory()).isEqualTo(history);
            assertThat(snapshot.contextState()).containsEntry("k", "v");
            assertThat(snapshot.createdAt()).isEqualTo(now);
        }
    }

    @Nested
    @DisplayName("Default Agent.snapshot()")
    class DefaultSnapshot {

        @Test
        @DisplayName("default Agent interface throws UnsupportedOperationException")
        void defaultThrows() {
            Agent agent =
                    new Agent() {
                        @Override
                        public Mono<Msg> call(Msg input) {
                            return Mono.empty();
                        }

                        @Override
                        public String id() {
                            return "test";
                        }

                        @Override
                        public String name() {
                            return "test";
                        }

                        @Override
                        public AgentState state() {
                            return AgentState.IDLE;
                        }

                        @Override
                        public void interrupt() {}
                    };

            assertThatThrownBy(agent::snapshot).isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Nested
    @DisplayName("Snapshot + Restore via AgentBuilder")
    class SnapshotRestore {

        @Test
        @DisplayName("snapshot captures conversation history from injectMessages")
        void snapshotCapturesHistory() {
            DefaultReActAgent agent = createMinimalAgent("test-agent");
            agent.injectMessages(
                    List.of(Msg.of(MsgRole.USER, "hello"), Msg.of(MsgRole.ASSISTANT, "world")));

            AgentSnapshot snapshot = agent.snapshot();

            assertThat(snapshot.agentId()).isEqualTo(agent.id());
            assertThat(snapshot.agentName()).isEqualTo("test-agent");
            assertThat(snapshot.conversationHistory()).hasSize(2);
            assertThat(snapshot.conversationHistory().get(0).text()).isEqualTo("hello");
            assertThat(snapshot.conversationHistory().get(1).text()).isEqualTo("world");
            assertThat(snapshot.contextState()).containsKey("modelName");
        }

        @Test
        @DisplayName("restoreFrom injects history into new agent")
        void restoreFromInjectsHistory() {
            // Create first agent and add history
            DefaultReActAgent original = createMinimalAgent("restore-test");
            original.injectMessages(
                    List.of(
                            Msg.of(MsgRole.USER, "question"),
                            Msg.of(MsgRole.ASSISTANT, "answer"),
                            Msg.of(MsgRole.USER, "follow-up")));

            AgentSnapshot snapshot = original.snapshot();

            // Restore into a new agent
            DefaultReActAgent restored = createMinimalAgent("restore-test");
            restored.injectMessages(snapshot.conversationHistory());

            assertThat(restored.conversationHistory()).hasSize(3);
            assertThat(restored.conversationHistory().get(2).text()).isEqualTo("follow-up");
        }

        @Test
        @DisplayName("snapshot → save → load → restore round-trip")
        void fullRoundTrip() {
            SnapshotStore store = new InMemorySnapshotStore();

            // Create agent and snapshot
            DefaultReActAgent original = createMinimalAgent("round-trip");
            original.injectMessages(List.of(Msg.of(MsgRole.USER, "test")));
            AgentSnapshot snapshot = original.snapshot();

            // Save
            store.save("round-trip:v1", snapshot).block();

            // Load and verify
            AgentSnapshot loaded = store.load("round-trip:v1").block();
            assertThat(loaded).isNotNull();
            assertThat(loaded.conversationHistory()).hasSize(1);

            // Restore into new agent
            DefaultReActAgent restored = createMinimalAgent("round-trip");
            restored.injectMessages(loaded.conversationHistory());

            assertThat(restored.conversationHistory()).isEqualTo(original.conversationHistory());
        }
    }

    private DefaultReActAgent createMinimalAgent(String name) {
        ModelProvider noop =
                new ModelProvider() {
                    @Override
                    public Mono<ModelResponse> call(List<Msg> messages, ModelConfig config) {
                        return Mono.empty();
                    }

                    @Override
                    public Flux<ModelResponse> stream(List<Msg> messages, ModelConfig config) {
                        return Flux.empty();
                    }

                    @Override
                    public String name() {
                        return "noop";
                    }
                };

        return (DefaultReActAgent)
                AgentBuilder.create().name(name).modelName("test-model").model(noop).build();
    }
}
