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

import static org.assertj.core.api.Assertions.assertThat;

import io.kairo.api.agent.AgentDiagnostics.ToolInvocationSnapshot;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

/**
 * Real-time "scar tissue" guard for the long-run stall behaviour — the compressed form of a
 * multi-hour session with a quiet long-running tool. Uses the real {@link StallDetector} poller (5s
 * cadence) with a tiny idle threshold so the invariants can be asserted in seconds.
 *
 * <p>Runs as an integration test (Failsafe) because it is timing-based and would slow the unit
 * suite.
 */
class StallDetectorToolSuppressionIT {

    private static ToolInvocationSnapshot snap(String id) {
        return new ToolInvocationSnapshot(id, "bash", Instant.now());
    }

    /** A legitimately long tool must NOT be mis-flagged as a stall while it is running. */
    @Test
    void longRunningToolIsNotFalselyStalled() throws InterruptedException {
        DefaultAgentDiagnostics diag = new DefaultAgentDiagnostics();
        StallDetector detector = new StallDetector(diag, 100); // 100ms idle threshold
        AtomicBoolean stalled = new AtomicBoolean(false);
        detector.stalled().subscribe(null, err -> {}, () -> stalled.set(true));

        diag.setActiveTool(snap("mvn-test")); // tool starts and stays active
        detector.start();

        // Past the first 5s poll: idle far exceeds 100ms but the active tool suppresses the check.
        Thread.sleep(6_500);
        assertThat(stalled).as("no false stall while a tool is active").isFalse();

        // Tool finishes → detector resumes and, after the idle window, fires.
        diag.clearActiveTool();
        Thread.sleep(6_500);
        assertThat(stalled).as("stall fires once the (now-idle) session goes quiet").isTrue();

        detector.dispose();
    }

    /** A leaked tool (clear never fired) must eventually self-heal so the stall is still caught. */
    @Test
    void leakedActiveToolSelfHealsIntoAStall() throws InterruptedException {
        DefaultAgentDiagnostics diag = new DefaultAgentDiagnostics();
        diag.setActiveToolHardLimitMs(200); // shrink the self-heal window for the test
        StallDetector detector = new StallDetector(diag, 100);
        AtomicBoolean stalled = new AtomicBoolean(false);
        detector.stalled().subscribe(null, err -> {}, () -> stalled.set(true));

        diag.setActiveTool(snap("hung-subagent")); // never cleared — simulates a lost doFinally
        detector.start();

        // By the first 5s poll the 200ms hard limit has long passed, so the leaked tool is
        // presumed gone and the genuine stall is detected instead of being suppressed forever.
        Thread.sleep(6_500);
        assertThat(stalled).as("leaked tool self-heals so a real hang is still surfaced").isTrue();

        detector.dispose();
    }
}
