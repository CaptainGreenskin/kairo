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

/**
 * Tests for LoopDetector Layer 3 — same (tool, args) pair repeated across consecutive responses.
 */
class LoopDetectorToolRepeatTest {

    // Use high hash/freq thresholds so only Layer 3 fires
    private static LoopDetector detector(int toolRepeatLimit) {
        return new LoopDetector(100, 200, 1000, 2000, Duration.ofMinutes(10), toolRepeatLimit);
    }

    private static List<Content.ToolUseContent> call(String tool, String path) {
        return List.of(new Content.ToolUseContent("id", tool, Map.of("path", path)));
    }

    private static List<Content.ToolUseContent> calls(String tool1, String tool2) {
        return List.of(
                new Content.ToolUseContent("id1", tool1, Map.of("arg", "v")),
                new Content.ToolUseContent("id2", tool2, Map.of("arg", "v")));
    }

    @Test
    void hardStopWhenSameToolAndArgsRepeatedNTimes() {
        LoopDetector d = detector(3);

        assertEquals(DetectionResult.Level.NONE, d.check(call("read_file", "foo.java")).level());
        assertEquals(DetectionResult.Level.NONE, d.check(call("read_file", "foo.java")).level());
        DetectionResult result = d.check(call("read_file", "foo.java"));

        assertEquals(DetectionResult.Level.HARD_STOP, result.level());
        assertTrue(result.message().contains("read_file"));
    }

    @Test
    void differentArgsDoNotTrigger() {
        LoopDetector d = detector(3);

        d.check(call("read_file", "foo.java"));
        d.check(call("read_file", "bar.java"));
        DetectionResult result = d.check(call("read_file", "foo.java"));

        // foo.java was not in the middle response, so consecutive window breaks
        assertEquals(DetectionResult.Level.NONE, result.level());
    }

    @Test
    void mixedToolCallsStillDetectsRepeat() {
        LoopDetector d = detector(3);
        // Each response includes read_file(foo.java) along with another tool
        List<Content.ToolUseContent> response1 =
                List.of(
                        new Content.ToolUseContent("a", "read_file", Map.of("path", "foo.java")),
                        new Content.ToolUseContent("b", "grep", Map.of("pattern", "import")));
        List<Content.ToolUseContent> response2 =
                List.of(
                        new Content.ToolUseContent("c", "read_file", Map.of("path", "foo.java")),
                        new Content.ToolUseContent("d", "analyze", Map.of("q", "unused")));
        List<Content.ToolUseContent> response3 =
                List.of(new Content.ToolUseContent("e", "read_file", Map.of("path", "foo.java")));

        assertEquals(DetectionResult.Level.NONE, d.check(response1).level());
        assertEquals(DetectionResult.Level.NONE, d.check(response2).level());
        DetectionResult result = d.check(response3);

        assertEquals(DetectionResult.Level.HARD_STOP, result.level());
        assertTrue(result.message().contains("read_file"));
    }

    @Test
    void resetClearsToolRepetitionHistory() {
        LoopDetector d = detector(3);

        d.check(call("read_file", "foo.java"));
        d.check(call("read_file", "foo.java"));
        d.reset();

        // After reset, two more calls should be NONE (window rebuilt from scratch)
        assertEquals(DetectionResult.Level.NONE, d.check(call("read_file", "foo.java")).level());
        assertEquals(DetectionResult.Level.NONE, d.check(call("read_file", "foo.java")).level());
    }
}
