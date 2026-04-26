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
package io.kairo.core.execution;

import static org.assertj.core.api.Assertions.assertThat;

import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RecoveryResultTest {

    @Test
    void fullConstructorStoresAllFields() {
        Msg msg = Msg.of(MsgRole.USER, "hello");
        RecoveryResult result =
                new RecoveryResult(
                        "exec-1",
                        3,
                        List.of(msg),
                        "cached-output",
                        false,
                        Map.of("tool-1", "result-1"),
                        List.of("tool-2"));

        assertThat(result.executionId()).isEqualTo("exec-1");
        assertThat(result.resumeFromIteration()).isEqualTo(3);
        assertThat(result.rebuiltHistory()).containsExactly(msg);
        assertThat(result.lastToolCallCachedResult()).isEqualTo("cached-output");
        assertThat(result.requiresHumanConfirmation()).isFalse();
        assertThat(result.cachedToolResults()).containsEntry("tool-1", "result-1");
        assertThat(result.interruptedToolCallIds()).containsExactly("tool-2");
    }

    @Test
    void backwardCompatConstructorDefaultsToolCallFields() {
        RecoveryResult result = new RecoveryResult("exec-2", 0, List.of(), null, true);

        assertThat(result.executionId()).isEqualTo("exec-2");
        assertThat(result.resumeFromIteration()).isEqualTo(0);
        assertThat(result.rebuiltHistory()).isEmpty();
        assertThat(result.lastToolCallCachedResult()).isNull();
        assertThat(result.requiresHumanConfirmation()).isTrue();
        assertThat(result.cachedToolResults()).isEmpty();
        assertThat(result.interruptedToolCallIds()).isEmpty();
    }

    @Test
    void requiresHumanConfirmationCanBeTrue() {
        RecoveryResult result = new RecoveryResult("exec-3", 1, List.of(), null, true);
        assertThat(result.requiresHumanConfirmation()).isTrue();
    }

    @Test
    void requiresHumanConfirmationCanBeFalse() {
        RecoveryResult result = new RecoveryResult("exec-4", 1, List.of(), null, false);
        assertThat(result.requiresHumanConfirmation()).isFalse();
    }

    @Test
    void nullLastToolCallCachedResultIsAllowed() {
        RecoveryResult result = new RecoveryResult("exec-5", 0, List.of(), null, false);
        assertThat(result.lastToolCallCachedResult()).isNull();
    }

    @Test
    void cachedToolResultsArePresentWhenProvided() {
        Map<String, String> cached = Map.of("id-1", "output-1", "id-2", "output-2");
        RecoveryResult result =
                new RecoveryResult("exec-6", 0, List.of(), null, false, cached, List.of());

        assertThat(result.cachedToolResults()).hasSize(2);
        assertThat(result.cachedToolResults()).containsKey("id-1");
    }

    @Test
    void interruptedToolCallIdsArePresentWhenProvided() {
        List<String> interrupted = List.of("tool-a", "tool-b");
        RecoveryResult result =
                new RecoveryResult("exec-7", 0, List.of(), null, false, Map.of(), interrupted);

        assertThat(result.interruptedToolCallIds()).containsExactlyInAnyOrder("tool-a", "tool-b");
    }
}
