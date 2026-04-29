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
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/** Tests for {@link IterationCheckpointManager}. */
class IterationCheckpointManagerTest {

    private List<Msg> testMessages(int count) {
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(i -> Msg.of(MsgRole.USER, "msg-" + i))
                .toList();
    }

    @Nested
    @DisplayName("Constructor")
    class ConstructorTests {

        @Test
        @DisplayName("throws on null store")
        void throwsOnNullStore() {
            assertThatThrownBy(() -> new IterationCheckpointManager(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("store must not be null");
        }
    }

    @Nested
    @DisplayName("save()")
    class SaveTests {

        @Test
        @DisplayName("saves checkpoint successfully")
        void savesCheckpointSuccessfully() {
            IterationCheckpointManager manager =
                    new IterationCheckpointManager(new InMemoryIterationCheckpointStore());
            List<Msg> messages = testMessages(3);

            StepVerifier.create(manager.save(5, messages)).verifyComplete();
        }

        @Test
        @DisplayName("errors on null messages")
        void errorsOnNullMessages() {
            IterationCheckpointManager manager =
                    new IterationCheckpointManager(new InMemoryIterationCheckpointStore());

            StepVerifier.create(manager.save(1, null))
                    .expectError(IllegalArgumentException.class)
                    .verify();
        }
    }

    @Nested
    @DisplayName("loadLast()")
    class LoadLastTests {

        @Test
        @DisplayName("returns empty when no checkpoints exist")
        void returnsEmptyWhenNoCheckpoints() {
            IterationCheckpointManager manager =
                    new IterationCheckpointManager(new InMemoryIterationCheckpointStore());

            StepVerifier.create(manager.loadLast())
                    .assertNext(opt -> assertThat(opt).isEmpty())
                    .verifyComplete();
        }

        @Test
        @DisplayName("returns last saved checkpoint")
        void returnsLastSavedCheckpoint() {
            IterationCheckpointManager manager =
                    new IterationCheckpointManager(new InMemoryIterationCheckpointStore());
            List<Msg> messages = testMessages(4);

            manager.save(5, messages).block();

            StepVerifier.create(manager.loadLast())
                    .assertNext(
                            opt -> {
                                assertThat(opt).isPresent();
                                IterationCheckpoint cp = opt.get();
                                assertThat(cp.iteration()).isEqualTo(5);
                                assertThat(cp.messages()).hasSize(4);
                            })
                    .verifyComplete();
        }

        @Test
        @DisplayName("returns latest when multiple checkpoints saved")
        void returnsLatestCheckpoint() {
            IterationCheckpointManager manager =
                    new IterationCheckpointManager(new InMemoryIterationCheckpointStore());

            manager.save(2, testMessages(1)).block();
            manager.save(7, testMessages(2)).block();
            manager.save(4, testMessages(3)).block();

            StepVerifier.create(manager.loadLast())
                    .assertNext(
                            opt -> {
                                assertThat(opt).isPresent();
                                assertThat(opt.get().iteration()).isEqualTo(7);
                                assertThat(opt.get().messages()).hasSize(2);
                            })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("deleteAll()")
    class DeleteAllTests {

        @Test
        @DisplayName("deletes all checkpoints")
        void deletesAllCheckpoints() {
            InMemoryIterationCheckpointStore store = new InMemoryIterationCheckpointStore();
            IterationCheckpointManager manager = new IterationCheckpointManager(store);

            manager.save(1, testMessages(1)).block();
            manager.save(2, testMessages(2)).block();
            manager.save(3, testMessages(3)).block();

            StepVerifier.create(manager.deleteAll()).verifyComplete();

            StepVerifier.create(manager.loadLast())
                    .assertNext(opt -> assertThat(opt).isEmpty())
                    .verifyComplete();
        }
    }

    /**
     * Minimal in-memory implementation of IterationCheckpointStore for testing. Keeps the latest 3
     * checkpoints to match the default behavior.
     */
    private static class InMemoryIterationCheckpointStore implements IterationCheckpointStore {

        private final java.util.Map<Integer, IterationCheckpoint> checkpoints =
                new java.util.concurrent.ConcurrentHashMap<>();

        @Override
        public Mono<Void> save(int iteration, List<Msg> messages) {
            checkpoints.put(
                    iteration,
                    new IterationCheckpoint(iteration, messages, java.time.Instant.now()));
            return Mono.empty();
        }

        @Override
        public Mono<Optional<IterationCheckpoint>> loadLast() {
            return Mono.fromSupplier(
                    () ->
                            checkpoints.keySet().stream()
                                    .max(Integer::compareTo)
                                    .map(checkpoints::get));
        }

        @Override
        public Mono<Void> deleteAll() {
            checkpoints.clear();
            return Mono.empty();
        }
    }
}
