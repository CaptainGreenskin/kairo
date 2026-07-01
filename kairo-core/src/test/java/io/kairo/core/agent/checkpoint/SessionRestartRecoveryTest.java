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

import io.kairo.api.agent.IterationCheckpoint;
import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import io.kairo.core.session.SessionSerializer;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Guards the session-rebuild-after-restart invariant that underpins unattended (overnight)
 * autonomy: a brand-new store instance pointed at the same on-disk directory — the way a restarted
 * server rehydrates a session — must resume from the LAST persisted iteration with the full
 * conversation intact, not from scratch.
 */
class SessionRestartRecoveryTest {

    @Test
    void freshInstanceResumesFromLastCheckpointWithFullHistory(@TempDir Path dir) {
        SessionSerializer serializer = new SessionSerializer();

        // Pre-restart process: a conversation that grows across three iterations.
        JsonFileIterationCheckpointStore before =
                new JsonFileIterationCheckpointStore(dir, serializer);
        before.save(0, List.of(Msg.of(MsgRole.USER, "do the task"))).block();
        before.save(
                        1,
                        List.of(
                                Msg.of(MsgRole.USER, "do the task"),
                                Msg.of(MsgRole.ASSISTANT, "step 1 done")))
                .block();
        before.save(
                        2,
                        List.of(
                                Msg.of(MsgRole.USER, "do the task"),
                                Msg.of(MsgRole.ASSISTANT, "step 1 done"),
                                Msg.of(MsgRole.ASSISTANT, "step 2 done")))
                .block();

        // Restarted process: a fresh instance on the same directory rebuilds from disk.
        JsonFileIterationCheckpointStore after =
                new JsonFileIterationCheckpointStore(dir, serializer);
        Optional<IterationCheckpoint> restored = after.loadLast().block();

        assertThat(restored).isPresent();
        // Resumes at the last iteration, not iteration 0 — no lost work.
        assertThat(restored.get().iteration()).isEqualTo(2);
        // Full conversation survived the restart.
        assertThat(restored.get().messages()).hasSize(3);
        // A resumed agent continues at cp.iteration + 1
        // (DefaultReActAgent.attemptCheckpointRestore).
        assertThat(restored.get().iteration() + 1).isEqualTo(3);
    }

    @Test
    void noCheckpointYieldsCleanStartNotAnError(@TempDir Path dir) {
        JsonFileIterationCheckpointStore store =
                new JsonFileIterationCheckpointStore(dir, new SessionSerializer());
        assertThat(store.loadLast().block()).isEmpty();
    }
}
