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
package io.kairo.core.agent;

import static org.junit.jupiter.api.Assertions.*;

import io.kairo.api.message.Content;
import io.kairo.core.agent.LoopDetector.DetectionResult;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link LoopDetector} — dual-layer loop detection. */
class LoopDetectorTest {

    private static List<Content.ToolUseContent> toolCalls(String... names) {
        return java.util.Arrays.stream(names)
                .map(n -> new Content.ToolUseContent("id-" + n, n, Map.of("arg", "value")))
                .toList();
    }

    private static List<Content.ToolUseContent> toolCallsWithArgs(
            String name, Map<String, Object> args) {
        return List.of(new Content.ToolUseContent("id-" + name, name, args));
    }

    // ---- Layer 1: Hash-based detection ----

    @Test
    void hashLayer_warnAfterThreeConsecutiveIdentical() {
        LoopDetector detector = LoopDetector.withDefaults();
        var calls = toolCalls("read_file");

        assertEquals(DetectionResult.Level.NONE, detector.check(calls).level());
        assertEquals(DetectionResult.Level.NONE, detector.check(calls).level());
        assertEquals(DetectionResult.Level.WARN, detector.check(calls).level());
    }

    @Test
    void hashLayer_hardStopAfterFiveConsecutiveIdentical() {
        LoopDetector detector = LoopDetector.withDefaults();
        var calls = toolCalls("read_file");

        for (int i = 0; i < 4; i++) {
            detector.check(calls);
        }
        DetectionResult result = detector.check(calls);
        assertEquals(DetectionResult.Level.HARD_STOP, result.level());
        assertTrue(result.message().contains("5"));
    }

    @Test
    void hashLayer_differentSequencesReturnNone() {
        LoopDetector detector = LoopDetector.withDefaults();

        assertEquals(DetectionResult.Level.NONE, detector.check(toolCalls("read_file")).level());
        assertEquals(DetectionResult.Level.NONE, detector.check(toolCalls("write_file")).level());
        assertEquals(DetectionResult.Level.NONE, detector.check(toolCalls("search")).level());
        assertEquals(DetectionResult.Level.NONE, detector.check(toolCalls("read_file")).level());
    }

    @Test
    void hashLayer_differentArgsProduceDifferentHashes() {
        LoopDetector detector = LoopDetector.withDefaults();

        for (int i = 0; i < 5; i++) {
            var calls = toolCallsWithArgs("read_file", Map.of("path", "file" + i + ".txt"));
            assertEquals(DetectionResult.Level.NONE, detector.check(calls).level());
        }
    }

    // ---- Layer 2: Frequency-based detection ----

    @Test
    void freqLayer_warnAfterThresholdCallsInWindow() {
        // Use low thresholds for testing
        LoopDetector detector = new LoopDetector(100, 200, 5, 10, Duration.ofMinutes(10), 1000);

        // Different args each time to avoid hash detection
        for (int i = 0; i < 4; i++) {
            var calls = toolCallsWithArgs("search", Map.of("q", "query" + i));
            detector.check(calls);
        }
        var result = detector.check(toolCallsWithArgs("search", Map.of("q", "query4")));
        assertEquals(DetectionResult.Level.WARN, result.level());
        assertTrue(result.message().contains("search"));
    }

    @Test
    void freqLayer_hardStopAfterHardLimitCallsInWindow() {
        LoopDetector detector = new LoopDetector(100, 200, 5, 10, Duration.ofMinutes(10), 1000);

        for (int i = 0; i < 9; i++) {
            detector.check(toolCallsWithArgs("search", Map.of("q", "query" + i)));
        }
        var result = detector.check(toolCallsWithArgs("search", Map.of("q", "query9")));
        assertEquals(DetectionResult.Level.HARD_STOP, result.level());
    }

    @Test
    void freqLayer_oldCallsOutsideWindowDoNotCount() {
        // Use a tiny window (1ms) so calls age out immediately
        LoopDetector detector = new LoopDetector(100, 200, 5, 10, Duration.ofMillis(1), 1000);

        for (int i = 0; i < 10; i++) {
            detector.check(toolCallsWithArgs("search", Map.of("q", "query" + i)));
            // Small sleep to ensure timestamps pass the 1ms window
            try {
                Thread.sleep(2);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        // All previous calls should be evicted; this should be NONE
        var result = detector.check(toolCallsWithArgs("search", Map.of("q", "final")));
        assertEquals(DetectionResult.Level.NONE, result.level());
    }

    // ---- Reset ----

    @Test
    void reset_clearsAllState() {
        LoopDetector detector = LoopDetector.withDefaults();
        var calls = toolCalls("read_file");

        // Build up to WARN
        detector.check(calls);
        detector.check(calls);
        assertEquals(DetectionResult.Level.WARN, detector.check(calls).level());

        // Reset and verify state is cleared
        detector.reset();
        assertEquals(DetectionResult.Level.NONE, detector.check(calls).level());
        assertEquals(DetectionResult.Level.NONE, detector.check(calls).level());
    }

    // ---- Mixed: highest severity wins ----

    @Test
    void mixed_highestSeverityWins() {
        // Set hash thresholds high (won't trigger), freq thresholds low (will trigger WARN)
        LoopDetector detector = new LoopDetector(100, 200, 3, 100, Duration.ofMinutes(10), 1000);
        var calls = toolCalls("tool_a");

        detector.check(calls);
        detector.check(calls);
        // 3rd call → freq WARN (3 calls to tool_a), hash is NONE (threshold 100)
        var result = detector.check(calls);
        assertEquals(DetectionResult.Level.WARN, result.level());
    }
}
