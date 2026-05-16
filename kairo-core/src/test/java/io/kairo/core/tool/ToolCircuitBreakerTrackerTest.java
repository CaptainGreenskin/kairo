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
package io.kairo.core.tool;

import static org.junit.jupiter.api.Assertions.*;

import io.kairo.api.tool.ToolOutcome;
import io.kairo.api.tool.ToolOutput;
import io.kairo.api.tool.ToolResult;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ToolCircuitBreakerTrackerTest {

    private ToolCircuitBreakerTracker tracker;

    @BeforeEach
    void setUp() {
        tracker = new ToolCircuitBreakerTracker(3); // threshold = 3
    }

    private ToolResult timeoutResult() {
        return new ToolResult(
                "test", new ToolOutput.Text("timeout"), ToolOutcome.TIMEOUT, List.of(), Map.of());
    }

    private ToolResult cancelledResult() {
        return new ToolResult(
                "test",
                new ToolOutput.Text("cancelled"),
                ToolOutcome.CANCELLED,
                List.of(),
                Map.of());
    }

    private ToolResult errorResult() {
        return ToolResult.error("test", "error");
    }

    private ToolResult successResult() {
        return ToolResult.success("test", "ok");
    }

    // ===== allowCall =====

    @Test
    void allowCall_trueWhenNoFailures() {
        assertTrue(tracker.allowCall("tool_a"));
    }

    @Test
    void allowCall_trueAfterFewerThanThresholdTimeouts() {
        tracker.track("tool_a", timeoutResult());
        tracker.track("tool_a", timeoutResult());
        // 2 timeouts, threshold = 3
        assertTrue(tracker.allowCall("tool_a"));
    }

    @Test
    void allowCall_falseAtThreshold() {
        tracker.track("tool_a", timeoutResult());
        tracker.track("tool_a", timeoutResult());
        tracker.track("tool_a", timeoutResult());
        // 3 timeouts == threshold
        assertFalse(tracker.allowCall("tool_a"));
    }

    @Test
    void allowCall_falseAboveThreshold() {
        for (int i = 0; i < 5; i++) {
            tracker.track("tool_a", timeoutResult());
        }
        assertFalse(tracker.allowCall("tool_a"));
    }

    // ===== Application errors do NOT trip breaker =====

    @Test
    void applicationErrors_doNotTripBreaker() {
        for (int i = 0; i < 10; i++) {
            tracker.track("tool_a", errorResult());
        }
        // Application errors should not count toward circuit breaker
        assertTrue(tracker.allowCall("tool_a"));
        assertEquals(0, tracker.getFailureCount("tool_a"));
    }

    @Test
    void cancelledResults_tripBreaker() {
        tracker.track("tool_a", cancelledResult());
        tracker.track("tool_a", cancelledResult());
        tracker.track("tool_a", cancelledResult());
        assertFalse(tracker.allowCall("tool_a"));
    }

    // ===== Success resets =====

    @Test
    void successfulResult_resetsFailureCount() {
        tracker.track("tool_a", timeoutResult());
        tracker.track("tool_a", timeoutResult());
        assertEquals(2, tracker.getFailureCount("tool_a"));

        tracker.track("tool_a", successResult());
        assertEquals(0, tracker.getFailureCount("tool_a"));
        assertTrue(tracker.allowCall("tool_a"));
    }

    // ===== Independent tracking =====

    @Test
    void differentTools_trackedIndependently() {
        tracker.track("tool_a", timeoutResult());
        tracker.track("tool_a", timeoutResult());
        tracker.track("tool_a", timeoutResult());

        // tool_a is tripped, tool_b should be fine
        assertFalse(tracker.allowCall("tool_a"));
        assertTrue(tracker.allowCall("tool_b"));
    }

    @Test
    void sessionScopedKeys_trackedIndependently() {
        tracker.track("tool_a::session1", timeoutResult());
        tracker.track("tool_a::session1", timeoutResult());
        tracker.track("tool_a::session1", timeoutResult());

        assertFalse(tracker.allowCall("tool_a::session1"));
        assertTrue(tracker.allowCall("tool_a::session2"));
    }

    // ===== Threshold boundary =====

    @Test
    void thresholdBoundary_nMinus1StillAllowed_nthBlocks() {
        // N-1 timeouts: still allowed
        for (int i = 0; i < 2; i++) {
            tracker.track("tool_a", timeoutResult());
        }
        assertTrue(tracker.allowCall("tool_a"));

        // N-th timeout: blocked
        tracker.track("tool_a", timeoutResult());
        assertFalse(tracker.allowCall("tool_a"));
    }

    // ===== getFailureCount =====

    @Test
    void getFailureCount_zeroForUnknownKey() {
        assertEquals(0, tracker.getFailureCount("nonexistent"));
    }

    @Test
    void getFailureCount_tracksIncrements() {
        tracker.track("tool_a", timeoutResult());
        assertEquals(1, tracker.getFailureCount("tool_a"));

        tracker.track("tool_a", timeoutResult());
        assertEquals(2, tracker.getFailureCount("tool_a"));
    }

    // ===== Reset =====

    @Test
    void reset_clearsAllState() {
        tracker.track("tool_a", timeoutResult());
        tracker.track("tool_b", timeoutResult());

        tracker.reset();

        assertEquals(0, tracker.getFailureCount("tool_a"));
        assertEquals(0, tracker.getFailureCount("tool_b"));
    }

    @Test
    void resetByToolName_clearsToolAndSessionKeys() {
        tracker.track("tool_a", timeoutResult());
        tracker.track("tool_a::session1", timeoutResult());
        tracker.track("tool_b", timeoutResult());

        tracker.reset("tool_a");

        assertEquals(0, tracker.getFailureCount("tool_a"));
        assertEquals(0, tracker.getFailureCount("tool_a::session1"));
        // tool_b is unaffected
        assertEquals(1, tracker.getFailureCount("tool_b"));
    }

    // ===== Default threshold =====

    @Test
    void invalidThreshold_defaultsToThree() {
        ToolCircuitBreakerTracker zeroTracker = new ToolCircuitBreakerTracker(0);
        zeroTracker.track("t", timeoutResult());
        zeroTracker.track("t", timeoutResult());
        assertTrue(zeroTracker.allowCall("t")); // 2 < 3 (default)

        zeroTracker.track("t", timeoutResult());
        assertFalse(zeroTracker.allowCall("t")); // 3 == 3 (default)
    }
}
