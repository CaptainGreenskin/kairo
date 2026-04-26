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

import io.kairo.api.agent.AgentSnapshot;
import io.kairo.api.agent.AgentState;
import io.kairo.api.agent.SnapshotStore;
import io.kairo.api.message.Content;
import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import io.kairo.core.session.SessionSerializer;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import reactor.test.StepVerifier;

/** Tests for {@link JsonFileSnapshotStore}. */
class JsonFileSnapshotStoreTest {

    @TempDir Path tempDir;

    private SnapshotStore store;

    @BeforeEach
    void setUp() {
        store = new JsonFileSnapshotStore(tempDir, new SessionSerializer());
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
                Instant.parse("2025-06-15T10:30:00Z"));
    }

    @Nested
    @DisplayName("Constructor validation")
    class ConstructorValidation {

        @Test
        @DisplayName("null storageDir throws IllegalArgumentException")
        void nullStorageDir() {
            assertThatThrownBy(() -> new JsonFileSnapshotStore(null, new SessionSerializer()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("storageDir");
        }

        @Test
        @DisplayName("null sessionSerializer throws IllegalArgumentException")
        void nullSessionSerializer() {
            assertThatThrownBy(() -> new JsonFileSnapshotStore(tempDir, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("sessionSerializer");
        }

        @Test
        @DisplayName("creates storage directory if it does not exist")
        void createsDirectory() {
            Path subDir = tempDir.resolve("snapshots");
            assertThat(subDir).doesNotExist();

            new JsonFileSnapshotStore(subDir, new SessionSerializer());

            assertThat(subDir).exists().isDirectory();
        }
    }

    @Nested
    @DisplayName("Save and Load")
    class SaveLoad {

        @Test
        @DisplayName("save then load returns equivalent snapshot")
        void saveAndLoad() {
            AgentSnapshot snapshot = testSnapshot("agent-1");

            StepVerifier.create(store.save("agent-1:latest", snapshot)).verifyComplete();

            StepVerifier.create(store.load("agent-1:latest"))
                    .assertNext(
                            loaded -> {
                                assertThat(loaded.agentId()).isEqualTo("agent-1");
                                assertThat(loaded.agentName()).isEqualTo("test-agent");
                                assertThat(loaded.state()).isEqualTo(AgentState.IDLE);
                                assertThat(loaded.iteration()).isEqualTo(5);
                                assertThat(loaded.totalTokensUsed()).isEqualTo(1000L);
                                assertThat(loaded.conversationHistory()).hasSize(2);
                                assertThat(loaded.conversationHistory().get(0).role())
                                        .isEqualTo(MsgRole.USER);
                                assertThat(loaded.conversationHistory().get(0).text())
                                        .isEqualTo("hello");
                                assertThat(loaded.conversationHistory().get(1).role())
                                        .isEqualTo(MsgRole.ASSISTANT);
                                assertThat(loaded.conversationHistory().get(1).text())
                                        .isEqualTo("hi");
                                assertThat(loaded.contextState())
                                        .containsEntry("modelName", "test-model");
                                assertThat(loaded.createdAt())
                                        .isEqualTo(Instant.parse("2025-06-15T10:30:00Z"));
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
            assertThat(loaded).isNotNull();
            assertThat(loaded.iteration()).isEqualTo(10);
            assertThat(loaded.state()).isEqualTo(AgentState.RUNNING);
        }

        @Test
        @DisplayName("persists as .json file on disk")
        void persistsAsFile() {
            store.save("agent-1:latest", testSnapshot("agent-1")).block();

            Path expectedFile = tempDir.resolve("agent-1_latest.json");
            assertThat(expectedFile).exists().isRegularFile();
        }

        @Test
        @DisplayName("snapshot with tool use content round-trips correctly")
        void toolUseContentRoundTrip() {
            Msg toolMsg =
                    Msg.builder()
                            .role(MsgRole.ASSISTANT)
                            .addContent(
                                    new Content.ToolUseContent(
                                            "tool-1", "calculator", Map.of("x", 42)))
                            .build();
            Msg toolResultMsg =
                    Msg.builder()
                            .role(MsgRole.TOOL)
                            .addContent(new Content.ToolResultContent("tool-1", "42", false))
                            .build();

            AgentSnapshot snapshot =
                    new AgentSnapshot(
                            "agent-tool",
                            "tool-agent",
                            AgentState.RUNNING,
                            2,
                            500L,
                            List.of(toolMsg, toolResultMsg),
                            Map.of(),
                            Instant.now());

            store.save("tool-test", snapshot).block();
            AgentSnapshot loaded = store.load("tool-test").block();

            assertThat(loaded).isNotNull();
            assertThat(loaded.conversationHistory()).hasSize(2);

            Content firstContent = loaded.conversationHistory().get(0).contents().get(0);
            assertThat(firstContent).isInstanceOf(Content.ToolUseContent.class);
            Content.ToolUseContent tuc = (Content.ToolUseContent) firstContent;
            assertThat(tuc.toolName()).isEqualTo("calculator");

            Content secondContent = loaded.conversationHistory().get(1).contents().get(0);
            assertThat(secondContent).isInstanceOf(Content.ToolResultContent.class);
            Content.ToolResultContent trc = (Content.ToolResultContent) secondContent;
            assertThat(trc.toolUseId()).isEqualTo("tool-1");
            assertThat(trc.content()).isEqualTo("42");
            assertThat(trc.isError()).isFalse();
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
                                            .containsExactlyInAnyOrder("agent-1_v1", "agent-1_v2"))
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

    @Nested
    @DisplayName("Key sanitization")
    class KeySanitization {

        @Test
        @DisplayName("replaces unsafe characters with underscore")
        void sanitizesUnsafeChars() {
            assertThat(JsonFileSnapshotStore.sanitizeKey("agent/1:latest"))
                    .isEqualTo("agent_1_latest");
            assertThat(JsonFileSnapshotStore.sanitizeKey("path\\to\\key")).isEqualTo("path_to_key");
            assertThat(JsonFileSnapshotStore.sanitizeKey("a*b?c\"d<e>f|g"))
                    .isEqualTo("a_b_c_d_e_f_g");
        }

        @Test
        @DisplayName("leaves safe characters untouched")
        void safeCharsUntouched() {
            assertThat(JsonFileSnapshotStore.sanitizeKey("agent-1_latest.v2"))
                    .isEqualTo("agent-1_latest.v2");
        }
    }
}
