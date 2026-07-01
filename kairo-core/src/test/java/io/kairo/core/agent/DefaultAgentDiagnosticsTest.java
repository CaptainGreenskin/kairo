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
import org.junit.jupiter.api.Test;

/**
 * Regression guard for the activeTool diagnostics that the {@link StallDetector} relies on to avoid
 * false stalls during legitimately long tool executions. Before this was wired, {@code
 * setActiveTool}/{@code clearActiveTool} were dead code and the detector suppression never fired —
 * a quiet tool running past the idle threshold was mis-flagged as a stall.
 */
class DefaultAgentDiagnosticsTest {

    private static ToolInvocationSnapshot snap(String id) {
        return new ToolInvocationSnapshot(id, "bash", Instant.now());
    }

    @Test
    void activeToolPresentWhileToolRunsThenClearedAfter() {
        DefaultAgentDiagnostics d = new DefaultAgentDiagnostics();
        assertThat(d.activeToolInvocation()).isEmpty();

        d.setActiveTool(snap("t1"));
        assertThat(d.activeToolInvocation()).isPresent();

        d.clearActiveTool();
        assertThat(d.activeToolInvocation()).isEmpty();
    }

    @Test
    void parallelToolsClearedOnlyByLastClear() {
        DefaultAgentDiagnostics d = new DefaultAgentDiagnostics();
        d.setActiveTool(snap("a"));
        d.setActiveTool(snap("b"));

        d.clearActiveTool(); // one sibling finished
        assertThat(d.activeToolInvocation()).isPresent(); // the other is still running

        d.clearActiveTool(); // last one finished
        assertThat(d.activeToolInvocation()).isEmpty();
    }

    @Test
    void strayClearDoesNotWedgeTheDepthCounter() {
        DefaultAgentDiagnostics d = new DefaultAgentDiagnostics();
        d.clearActiveTool(); // stray clear with nothing active — must not push depth negative

        d.setActiveTool(snap("a"));
        assertThat(d.activeToolInvocation()).isPresent();

        d.clearActiveTool();
        assertThat(d.activeToolInvocation()).isEmpty();
    }

    @Test
    void selfHealsWhenClearIsMissed() throws InterruptedException {
        DefaultAgentDiagnostics d = new DefaultAgentDiagnostics();
        d.setActiveToolHardLimitMs(20);

        d.setActiveTool(snap("leaked")); // simulate a reactive chain whose doFinally never fired
        assertThat(d.activeToolInvocation()).isPresent();

        Thread.sleep(50);
        // Past the hard limit the leaked tool is presumed gone so the StallDetector is no longer
        // suppressed forever.
        assertThat(d.activeToolInvocation()).isEmpty();
    }

    @Test
    void clearRefreshesStallClockSoLongToolsDoNotTripImmediately() throws InterruptedException {
        DefaultAgentDiagnostics d = new DefaultAgentDiagnostics();
        d.setActiveTool(snap("t"));
        Thread.sleep(40);

        d.clearActiveTool();
        // The moment a long tool finishes the idle clock resets, so the detector doesn't fire
        // before the next model call has a chance to start.
        assertThat(d.msSinceLastEvent()).isLessThan(30);
    }
}
