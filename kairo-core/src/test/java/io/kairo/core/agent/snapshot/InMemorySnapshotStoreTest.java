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

import io.kairo.api.agent.AgentSnapshot;
import io.kairo.api.agent.AgentState;
import io.kairo.api.agent.SnapshotStore;
import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

/** Tests for {@link InMemorySnapshotStore}. */
class InMemorySnapshotStoreTest {

    private SnapshotStore store;

    @BeforeEach
    void setUp() {
        store = new InMemorySnapshotStore();
    }

    private AgentSnapshot testSnapshot(String agentId) {
        return new AgentSnapshot(
                agentId,
                "test-agent",
                AgentState.IDLE,
                5,
                1000L,
                List.of(Msg.of(MsgRole.USER, "hello"), Msg.of(MsgRole.ASSISTANT, "hi")),
                Map.of("modelName", "test-model"),
                Instant.now());
    }

    @Nested
    @DisplayName("Save and Load")
    class SaveLoad {

        @Test
        @DisplayName("save then load returns same snapshot")
        void saveAndLoad() {
            AgentSnapshot snapshot = testSnapshot("agent-1");

            StepVerifier.create(store.save("agent-1:latest", snapshot)).verifyComplete();

            StepVerifier.create(store.load("agent-1:latest"))
                    .assertNext(
                            loaded -> {
                                assertThat(loaded.agentId()).isEqualTo("agent-1");
                                assertThat(loaded.iteration()).isEqualTo(5);
                                assertThat(loaded.totalTokensUsed()).isEqualTo(1000L);
                                assertThat(loaded.conversationHistory()).hasSize(2);
                                assertThat(loaded.contextState())
                                        .containsEntry("modelName", "test-model");
                            })
                    .verifyComplete();
        }

        @Test
        @DisplayName("load nonexistent key returns empty")
        void loadNonexistent() {
            StepVerifier.create(store.load("no-such-key")).verifyComplete();
        }

        @Test
        @DisplayName("save overwrites previous snapshot")
        void saveOverwrites() {
            AgentSnapshot v1 = testSnapshot("agent-1");
            AgentSnapshot v2 =
                    new AgentSnapshot(
                            "agent-1",
                            "test-agent",
                            AgentState.RUNNING,
                            10,
                            2000L,
                            List.of(),
                            Map.of(),
                            Instant.now());

            store.save("agent-1:latest", v1).block();
            store.save("agent-1:latest", v2).block();

            AgentSnapshot loaded = store.load("agent-1:latest").block();
            assertThat(loaded.iteration()).isEqualTo(10);
            assertThat(loaded.state()).isEqualTo(AgentState.RUNNING);
        }
    }

    @Nested
    @DisplayName("Delete")
    class Delete {

        @Test
        @DisplayName("delete removes snapshot")
        void deleteRemoves() {
            store.save("agent-1:latest", testSnapshot("agent-1")).block();
            store.delete("agent-1:latest").block();

            StepVerifier.create(store.load("agent-1:latest")).verifyComplete();
        }

        @Test
        @DisplayName("delete nonexistent key is a no-op")
        void deleteNonexistent() {
            StepVerifier.create(store.delete("no-such-key")).verifyComplete();
        }
    }

    @Nested
    @DisplayName("List keys")
    class ListKeys {

        @Test
        @DisplayName("listKeys filters by prefix")
        void listKeysByPrefix() {
            store.save("agent-1:v1", testSnapshot("agent-1")).block();
            store.save("agent-1:v2", testSnapshot("agent-1")).block();
            store.save("agent-2:v1", testSnapshot("agent-2")).block();

            StepVerifier.create(store.listKeys("agent-1").collectList())
                    .assertNext(
                            keys ->
                                    assertThat(keys)
                                            .containsExactlyInAnyOrder("agent-1:v1", "agent-1:v2"))
                    .verifyComplete();
        }

        @Test
        @DisplayName("listKeys with empty prefix returns all")
        void listKeysAll() {
            store.save("a:1", testSnapshot("a")).block();
            store.save("b:1", testSnapshot("b")).block();

            StepVerifier.create(store.listKeys("").collectList())
                    .assertNext(keys -> assertThat(keys).hasSize(2))
                    .verifyComplete();
        }

        @Test
        @DisplayName("listKeys with no match returns empty")
        void listKeysNoMatch() {
            store.save("a:1", testSnapshot("a")).block();

            StepVerifier.create(store.listKeys("z").collectList())
                    .assertNext(keys -> assertThat(keys).isEmpty())
                    .verifyComplete();
        }
    }
}
