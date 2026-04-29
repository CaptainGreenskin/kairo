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

import org.junit.jupiter.api.Test;

/** Unit tests for {@link ToolCallHistory}. */
class ToolCallHistoryTest {

    // ---- Default thresholds (warn=3, abort=5) ----

    @Test
    void firstTwoIdenticalCalls_areOk() {
        ToolCallHistory history = new ToolCallHistory(3, 5);
        assertEquals(ToolCallHistory.Status.OK, history.record("bash", "mvn test"));
        assertEquals(ToolCallHistory.Status.OK, history.record("bash", "mvn test"));
    }

    @Test
    void thirdIdenticalCall_triggersWarn() {
        ToolCallHistory history = new ToolCallHistory(3, 5);
        history.record("bash", "mvn test");
        history.record("bash", "mvn test");
        assertEquals(ToolCallHistory.Status.WARN, history.record("bash", "mvn test"));
    }

    @Test
    void fifthIdenticalCall_triggersAbort() {
        ToolCallHistory history = new ToolCallHistory(3, 5);
        history.record("bash", "mvn test");
        history.record("bash", "mvn test");
        history.record("bash", "mvn test"); // WARN
        history.record("bash", "mvn test");
        assertEquals(ToolCallHistory.Status.ABORT, history.record("bash", "mvn test"));
    }

    // ---- Different tools/args do not trigger ----

    @Test
    void differentTools_doNotAffectCounting() {
        ToolCallHistory history = new ToolCallHistory(3, 5);
        history.record("bash", "mvn test");
        history.record("read_file", "foo.java");
        history.record("bash", "mvn test");
        assertEquals(ToolCallHistory.Status.OK, history.record("read_file", "bar.java"));
    }

    @Test
    void differentArgs_sameTool_doNotTrigger() {
        ToolCallHistory history = new ToolCallHistory(3, 5);
        history.record("read_file", "foo.java");
        history.record("read_file", "bar.java");
        assertEquals(ToolCallHistory.Status.OK, history.record("read_file", "baz.java"));
    }

    // ---- Reset ----

    @Test
    void reset_clearsCounting() {
        ToolCallHistory history = new ToolCallHistory(3, 5);
        history.record("bash", "mvn test");
        history.record("bash", "mvn test");
        assertEquals(ToolCallHistory.Status.WARN, history.record("bash", "mvn test"));

        history.reset();
        assertEquals(ToolCallHistory.Status.OK, history.record("bash", "mvn test"));
        assertEquals(ToolCallHistory.Status.OK, history.record("bash", "mvn test"));
    }

    // ---- Custom thresholds ----

    @Test
    void customThresholds_warnAndAbort() {
        ToolCallHistory history = new ToolCallHistory(2, 4);
        assertEquals(ToolCallHistory.Status.OK, history.record("x", "a"));
        assertEquals(ToolCallHistory.Status.WARN, history.record("x", "a")); // 2nd = warn
        // 3rd = still WARN (not yet at abort=4)
        assertEquals(ToolCallHistory.Status.WARN, history.record("x", "a"));
        assertEquals(ToolCallHistory.Status.ABORT, history.record("x", "a")); // 4th = abort
    }

    @Test
    void invalidThresholds_throwException() {
        assertThrows(IllegalArgumentException.class, () -> new ToolCallHistory(0, 5));
        assertThrows(IllegalArgumentException.class, () -> new ToolCallHistory(5, 5));
        assertThrows(IllegalArgumentException.class, () -> new ToolCallHistory(6, 5));
    }

    // ---- Accessors ----

    @Test
    void accessors_returnCorrectValues() {
        ToolCallHistory history = new ToolCallHistory(3, 7);
        assertEquals(3, history.warnAt());
        assertEquals(7, history.abortAt());
    }

    // ---- No-arg constructor uses defaults ----

    @Test
    void defaultConstructor_usesDefaultThresholds() {
        ToolCallHistory history = new ToolCallHistory();
        assertEquals(3, history.warnAt());
        assertEquals(5, history.abortAt());
    }

    // ---- Streak break ----

    @Test
    void streakBreak_oldEntriesSlideOut() {
        // With abortAt=5, the ring buffer holds at most 5 entries.
        // Old identical entries slide out as new different calls are added.
        ToolCallHistory history = new ToolCallHistory(3, 5);

        // Fill with 5 bash calls → ABORT
        for (int i = 0; i < 4; i++) {
            history.record("bash", "mvn test");
        }
        assertEquals(ToolCallHistory.Status.ABORT, history.record("bash", "mvn test"));

        history.reset();

        // After reset: 2 bash, 3 different → all bash entries eventually slide out
        history.record("bash", "mvn test");
        history.record("bash", "mvn test");
        history.record("read_file", "a.java");
        history.record("read_file", "b.java");
        history.record("grep", "pattern");
        // Buffer is full [bash, bash, read_file, read_file, grep], bash count = 2 → OK
        assertEquals(ToolCallHistory.Status.OK, history.record("bash", "mvn test"));
        // Buffer: [bash, read_file, read_file, grep, bash], bash count = 2 → OK
        assertEquals(ToolCallHistory.Status.OK, history.record("bash", "mvn test"));
        // 3rd bash would hit WARN — that's expected since the window still has 2 old bash entries
    }

    // ---- Null args ----

    @Test
    void nullArgs_handledGracefully() {
        ToolCallHistory history = new ToolCallHistory(3, 5);
        assertEquals(ToolCallHistory.Status.OK, history.record("bash", null));
        assertEquals(ToolCallHistory.Status.OK, history.record("bash", null));
        assertEquals(ToolCallHistory.Status.WARN, history.record("bash", null));
    }
}
