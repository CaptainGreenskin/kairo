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
package io.kairo.core.agent.checkpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.kairo.api.agent.IterationCheckpoint;
import io.kairo.api.agent.IterationCheckpointStore;
import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import io.kairo.core.session.SessionSerializer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import reactor.test.StepVerifier;

/** Tests for {@link JsonFileIterationCheckpointStore}. */
class JsonFileIterationCheckpointStoreTest {

    @TempDir Path tempDir;

    private IterationCheckpointStore store;

    @BeforeEach
    void setUp() {
        store = new JsonFileIterationCheckpointStore(tempDir, new SessionSerializer());
    }

    private List<Msg> testMessages(int count) {
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(i -> Msg.of(MsgRole.USER, "message-" + i))
                .toList();
    }

    @Nested
    @DisplayName("Constructor")
    class ConstructorTests {

        @Test
        @DisplayName("throws on null storageDir")
        void throwsOnNullStorageDir() {
            assertThatThrownBy(
                            () ->
                                    new JsonFileIterationCheckpointStore(
                                            null, new SessionSerializer()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("storageDir must not be null");
        }

        @Test
        @DisplayName("throws on null sessionSerializer")
        void throwsOnNullSessionSerializer() {
            assertThatThrownBy(() -> new JsonFileIterationCheckpointStore(tempDir, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("sessionSerializer must not be null");
        }

        @Test
        @DisplayName("throws on maxRetained < 1")
        void throwsOnInvalidMaxRetained() {
            assertThatThrownBy(
                            () ->
                                    new JsonFileIterationCheckpointStore(
                                            tempDir, new SessionSerializer(), 0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("maxRetained must be >= 1");
        }

        @Test
        @DisplayName("creates storage directory if it does not exist")
        void createsStorageDirectory() {
            Path subdir = tempDir.resolve("sub/checkpoints");
            new JsonFileIterationCheckpointStore(subdir, new SessionSerializer());
            assertThat(Files.exists(subdir)).isTrue();
        }
    }

    @Nested
    @DisplayName("save()")
    class SaveTests {

        @Test
        @DisplayName("saves checkpoint with messages")
        void savesCheckpointWithMessages() {
            List<Msg> messages = testMessages(3);

            StepVerifier.create(store.save(5, messages)).verifyComplete();

            // Verify files exist
            assertThat(Files.exists(tempDir.resolve("iteration-5.json"))).isTrue();
            assertThat(Files.exists(tempDir.resolve("iteration-5-messages.json"))).isTrue();
        }

        @Test
        @DisplayName("overwrites existing checkpoint for same iteration")
        void overwritesExistingCheckpoint() {
            List<Msg> first = List.of(Msg.of(MsgRole.USER, "first"));
            List<Msg> second = List.of(Msg.of(MsgRole.USER, "second"));

            store.save(3, first).block();
            store.save(3, second).block();

            Optional<IterationCheckpoint> loaded = store.loadLast().block();
            assertThat(loaded).isPresent();
            assertThat(loaded.get().iteration()).isEqualTo(3);
            assertThat(loaded.get().messages()).hasSize(1);
            assertThat(loaded.get().messages().get(0).text()).isEqualTo("second");
        }
    }

    @Nested
    @DisplayName("loadLast()")
    class LoadLastTests {

        @Test
        @DisplayName("returns empty when no checkpoints exist")
        void returnsEmptyWhenNoCheckpoints() {
            StepVerifier.create(store.loadLast())
                    .assertNext(opt -> assertThat(opt).isEmpty())
                    .verifyComplete();
        }

        @Test
        @DisplayName("returns the highest iteration checkpoint")
        void returnsHighestIteration() {
            store.save(2, testMessages(1)).block();
            store.save(5, testMessages(2)).block();
            store.save(3, testMessages(3)).block();

            Optional<IterationCheckpoint> loaded = store.loadLast().block();
            assertThat(loaded).isPresent();
            assertThat(loaded.get().iteration()).isEqualTo(5);
            assertThat(loaded.get().messages()).hasSize(2);
        }

        @Test
        @DisplayName("returns correct messages for loaded checkpoint")
        void returnsCorrectMessages() {
            List<Msg> messages =
                    List.of(
                            Msg.of(MsgRole.USER, "hello"),
                            Msg.of(MsgRole.ASSISTANT, "hi there"),
                            Msg.of(MsgRole.USER, "how are you?"));

            store.save(7, messages).block();

            Optional<IterationCheckpoint> loaded = store.loadLast().block();
            assertThat(loaded).isPresent();
            assertThat(loaded.get().iteration()).isEqualTo(7);
            assertThat(loaded.get().messages()).hasSize(3);
            assertThat(loaded.get().messages().get(0).text()).isEqualTo("hello");
            assertThat(loaded.get().messages().get(1).text()).isEqualTo("hi there");
            assertThat(loaded.get().messages().get(2).text()).isEqualTo("how are you?");
        }

        @Test
        @DisplayName("returns empty when storage directory does not exist")
        void returnsEmptyWhenDirectoryMissing() {
            IterationCheckpointStore store2 =
                    new JsonFileIterationCheckpointStore(
                            tempDir.resolve("nonexistent"), new SessionSerializer());
            StepVerifier.create(store2.loadLast())
                    .assertNext(opt -> assertThat(opt).isEmpty())
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("pruning")
    class PruningTests {

        @Test
        @DisplayName("default retention (20) keeps recent checkpoints for rewind depth")
        void defaultRetentionKeepsRecentCheckpoints() {
            // Save 5 checkpoints — well within the default retention window, so none are pruned
            // (the default is generous precisely so rewind can reach several turns back).
            for (int i = 1; i <= 5; i++) {
                store.save(i, testMessages(i)).block();
            }

            List<String> files = listMetaFiles();
            assertThat(files)
                    .containsExactlyInAnyOrder(
                            "iteration-1.json",
                            "iteration-2.json",
                            "iteration-3.json",
                            "iteration-4.json",
                            "iteration-5.json");

            Optional<IterationCheckpoint> loaded = store.loadLast().block();
            assertThat(loaded).isPresent();
            assertThat(loaded.get().iteration()).isEqualTo(5);
        }

        @Test
        @DisplayName("custom maxRetained keeps specified number")
        void customMaxRetained() {
            IterationCheckpointStore store2 =
                    new JsonFileIterationCheckpointStore(tempDir, new SessionSerializer(), 2);

            for (int i = 1; i <= 4; i++) {
                store2.save(i, testMessages(i)).block();
            }

            List<String> files = listMetaFiles();
            assertThat(files).containsExactlyInAnyOrder("iteration-3.json", "iteration-4.json");
        }

        @Test
        @DisplayName("loadAt targets a specific iteration (rewind), empty when pruned/absent")
        void loadAtTargetsSpecificIteration() {
            JsonFileIterationCheckpointStore concrete =
                    new JsonFileIterationCheckpointStore(tempDir, new SessionSerializer());
            for (int i = 1; i <= 3; i++) {
                concrete.save(i, testMessages(i)).block();
            }

            Optional<IterationCheckpoint> mid = concrete.loadAt(2).block();
            assertThat(mid).isPresent();
            assertThat(mid.get().iteration()).isEqualTo(2);

            assertThat(concrete.loadAt(99).block()).isEmpty();
        }

        @Test
        @DisplayName("deleteAfter discards later iterations so loadLast returns the rewind target")
        void deleteAfterTruncatesToRewindTarget() {
            JsonFileIterationCheckpointStore concrete =
                    new JsonFileIterationCheckpointStore(tempDir, new SessionSerializer());
            for (int i = 0; i <= 4; i++) {
                concrete.save(i, testMessages(i)).block();
            }

            concrete.deleteAfter(2).block();

            List<String> files = listMetaFiles();
            assertThat(files)
                    .containsExactlyInAnyOrder(
                            "iteration-0.json", "iteration-1.json", "iteration-2.json");
            Optional<IterationCheckpoint> last = concrete.loadLast().block();
            assertThat(last).isPresent();
            assertThat(last.get().iteration()).isEqualTo(2);
        }

        @Test
        @DisplayName("no pruning when under limit")
        void noPruningWhenUnderLimit() {
            store.save(1, testMessages(1)).block();
            store.save(2, testMessages(2)).block();

            List<String> files = listMetaFiles();
            assertThat(files).containsExactlyInAnyOrder("iteration-1.json", "iteration-2.json");
        }
    }

    @Nested
    @DisplayName("deleteAll()")
    class DeleteAllTests {

        @Test
        @DisplayName("removes all checkpoint files")
        void removesAllCheckpoints() {
            store.save(1, testMessages(1)).block();
            store.save(2, testMessages(2)).block();
            store.save(3, testMessages(3)).block();

            StepVerifier.create(store.deleteAll()).verifyComplete();

            List<String> files = listMetaFiles();
            assertThat(files).isEmpty();
        }

        @Test
        @DisplayName("succeeds when no checkpoints exist")
        void succeedsWhenNoCheckpoints() {
            StepVerifier.create(store.deleteAll()).verifyComplete();
        }
    }

    private List<String> listMetaFiles() {
        try (Stream<Path> stream = Files.list(tempDir)) {
            return stream.filter(
                            p -> {
                                String name = p.getFileName().toString();
                                return name.startsWith("iteration-")
                                        && name.endsWith(".json")
                                        && !name.contains("-messages.json");
                            })
                    .map(p -> p.getFileName().toString())
                    .toList();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
