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
package io.kairo.core.governance;

import static org.junit.jupiter.api.Assertions.*;

import io.kairo.api.hook.HookResult;
import io.kairo.api.hook.PostReasoningEvent;
import io.kairo.api.hook.PreReasoningEvent;
import io.kairo.api.hook.ToolResultEvent;
import io.kairo.api.message.Content;
import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import io.kairo.api.model.ModelConfig;
import io.kairo.api.model.ModelResponse;
import io.kairo.api.tool.ToolResult;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class GovernanceHookPackTest {

    // ── MaxTurnsGuard ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("MaxTurnsGuard")
    class MaxTurnsGuardTests {

        @Test
        @DisplayName("proceeds when under warn threshold")
        void proceedsUnderThreshold() {
            var guard = new MaxTurnsGuard(5, 10, false);
            var event = postReasoning();

            for (int i = 0; i < 4; i++) {
                var result = guard.onPostReasoning(event);
                assertEquals(HookResult.Decision.CONTINUE, result.decision());
            }
        }

        @Test
        @DisplayName("injects warn at warn threshold")
        void injectsWarnAtThreshold() {
            var guard = new MaxTurnsGuard(3, 6, false);
            var event = postReasoning();

            guard.onPostReasoning(event); // 1
            guard.onPostReasoning(event); // 2
            var result = guard.onPostReasoning(event); // 3 → warn

            assertEquals(HookResult.Decision.INJECT, result.decision());
            assertTrue(result.injectedMessage().text().contains("3 turns"));
            assertEquals("MaxTurnsGuard", result.hookSource());
        }

        @Test
        @DisplayName("warn fires only once")
        void warnFiresOnlyOnce() {
            var guard = new MaxTurnsGuard(2, 10, false);
            var event = postReasoning();

            guard.onPostReasoning(event); // 1
            guard.onPostReasoning(event); // 2 → warn
            var result = guard.onPostReasoning(event); // 3 → should proceed (warn already fired)

            assertEquals(HookResult.Decision.CONTINUE, result.decision());
        }

        @Test
        @DisplayName("injects force at force threshold")
        void injectsForceAtThreshold() {
            var guard = new MaxTurnsGuard(2, 4, false);
            var event = postReasoning();

            guard.onPostReasoning(event); // 1
            guard.onPostReasoning(event); // 2 → warn
            guard.onPostReasoning(event); // 3
            var result = guard.onPostReasoning(event); // 4 → force

            assertEquals(HookResult.Decision.INJECT, result.decision());
            assertTrue(result.injectedMessage().text().contains("STOP"));
        }

        @Test
        @DisplayName("suppressed in interactive mode")
        void suppressedInInteractive() {
            var guard = new MaxTurnsGuard(2, 4, true);
            var event = postReasoning();

            for (int i = 0; i < 10; i++) {
                var result = guard.onPostReasoning(event);
                assertEquals(HookResult.Decision.CONTINUE, result.decision());
            }
        }

        @Test
        @DisplayName("reset clears state")
        void resetClearsState() {
            var guard = new MaxTurnsGuard(2, 4, false);
            var event = postReasoning();

            guard.onPostReasoning(event); // 1
            guard.onPostReasoning(event); // 2 → warn
            assertTrue(guard.isWarnFired());

            guard.reset();
            assertFalse(guard.isWarnFired());
            assertEquals(0, guard.turnCount());
        }
    }

    // ── ToolCallBudgetGuard ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("ToolCallBudgetGuard")
    class ToolCallBudgetGuardTests {

        @Test
        @DisplayName("proceeds when under threshold")
        void proceedsUnderThreshold() {
            var guard = new ToolCallBudgetGuard(5, 10, false);
            var event = toolResult("bash");

            for (int i = 0; i < 4; i++) {
                assertEquals(HookResult.Decision.CONTINUE, guard.onToolResult(event).decision());
            }
        }

        @Test
        @DisplayName("injects at warn threshold")
        void injectsAtWarnThreshold() {
            var guard = new ToolCallBudgetGuard(3, 6, false);
            var event = toolResult("read");

            guard.onToolResult(event); // 1
            guard.onToolResult(event); // 2
            var result = guard.onToolResult(event); // 3 → warn

            assertEquals(HookResult.Decision.INJECT, result.decision());
            assertTrue(result.injectedMessage().text().contains("3 tool calls"));
        }

        @Test
        @DisplayName("tracks count correctly")
        void tracksCount() {
            var guard = new ToolCallBudgetGuard(100, 200, false);
            for (int i = 0; i < 7; i++) {
                guard.onToolResult(toolResult("edit"));
            }
            assertEquals(7, guard.callCount());
        }
    }

    // ── ContextSizeGuard ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("ContextSizeGuard")
    class ContextSizeGuardTests {

        @Test
        @DisplayName("proceeds when context is small")
        void proceedsWhenSmall() {
            var guard = new ContextSizeGuard(1000, 2000);
            var event = preReasoning(List.of(Msg.of(MsgRole.USER, "hello")));
            var result = guard.onPreReasoning(event);
            assertEquals(HookResult.Decision.CONTINUE, result.decision());
        }

        @Test
        @DisplayName("active in interactive mode (does not suppress)")
        void activeInInteractive() {
            var guard = new ContextSizeGuard(1, 2, true);
            var event = preReasoning(List.of(Msg.of(MsgRole.USER, "hello world")));
            var result = guard.onPreReasoning(event);
            assertEquals(HookResult.Decision.INJECT, result.decision());
        }
    }

    // ── RepetitiveToolGuard ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("RepetitiveToolGuard")
    class RepetitiveToolGuardTests {

        @Test
        @DisplayName("proceeds when tools vary")
        void proceedsWhenToolsVary() {
            var guard = new RepetitiveToolGuard(3, false);

            assertEquals(
                    HookResult.Decision.CONTINUE,
                    guard.onPostReasoning(postReasoningWithTool("read")).decision());
            assertEquals(
                    HookResult.Decision.CONTINUE,
                    guard.onPostReasoning(postReasoningWithTool("edit")).decision());
            assertEquals(
                    HookResult.Decision.CONTINUE,
                    guard.onPostReasoning(postReasoningWithTool("bash")).decision());
        }

        @Test
        @DisplayName("injects when same tool repeated N times")
        void injectsOnRepetition() {
            var guard = new RepetitiveToolGuard(3, false);

            guard.onPostReasoning(postReasoningWithTool("read")); // 1
            guard.onPostReasoning(postReasoningWithTool("read")); // 2
            var result = guard.onPostReasoning(postReasoningWithTool("read")); // 3 → inject

            assertEquals(HookResult.Decision.INJECT, result.decision());
            assertTrue(result.injectedMessage().text().contains("read"));
            assertTrue(result.injectedMessage().text().contains("3 times"));
        }

        @Test
        @DisplayName("fires only once per tool")
        void firesOncePerTool() {
            var guard = new RepetitiveToolGuard(2, false);

            guard.onPostReasoning(postReasoningWithTool("read")); // 1
            guard.onPostReasoning(postReasoningWithTool("read")); // 2 → inject
            var result = guard.onPostReasoning(postReasoningWithTool("read")); // 3 → no

            assertEquals(HookResult.Decision.CONTINUE, result.decision());
        }

        @Test
        @DisplayName("resets consecutive count on different tool")
        void resetsOnDifferentTool() {
            var guard = new RepetitiveToolGuard(3, false);

            guard.onPostReasoning(postReasoningWithTool("read")); // 1
            guard.onPostReasoning(postReasoningWithTool("read")); // 2
            guard.onPostReasoning(postReasoningWithTool("edit")); // reset
            var result = guard.onPostReasoning(postReasoningWithTool("read")); // 1

            assertEquals(HookResult.Decision.CONTINUE, result.decision());
        }

        @Test
        @DisplayName("suppressed in interactive mode")
        void suppressedInInteractive() {
            var guard = new RepetitiveToolGuard(2, true);

            guard.onPostReasoning(postReasoningWithTool("read"));
            guard.onPostReasoning(postReasoningWithTool("read"));
            var result = guard.onPostReasoning(postReasoningWithTool("read"));

            assertEquals(HookResult.Decision.CONTINUE, result.decision());
        }
    }

    // ── GovernanceHookPack factory ──────────────────────────────────────────────

    @Nested
    @DisplayName("GovernanceHookPack factory")
    class FactoryTests {

        @Test
        @DisplayName("defaults() returns 4 hooks")
        void defaultsReturns4() {
            var hooks = GovernanceHookPack.defaults();
            assertEquals(4, hooks.size());
            assertInstanceOf(MaxTurnsGuard.class, hooks.get(0));
            assertInstanceOf(ContextSizeGuard.class, hooks.get(1));
            assertInstanceOf(ToolCallBudgetGuard.class, hooks.get(2));
            assertInstanceOf(RepetitiveToolGuard.class, hooks.get(3));
        }

        @Test
        @DisplayName("strict() uses tighter thresholds")
        void strictTighter() {
            var hooks = GovernanceHookPack.strict();
            var maxTurns = (MaxTurnsGuard) hooks.get(0);
            assertEquals(10, maxTurns.warnThreshold());
            assertEquals(15, maxTurns.forceThreshold());
        }

        @Test
        @DisplayName("relaxed() uses looser thresholds")
        void relaxedLooser() {
            var hooks = GovernanceHookPack.relaxed();
            var maxTurns = (MaxTurnsGuard) hooks.get(0);
            assertEquals(50, maxTurns.warnThreshold());
            assertEquals(80, maxTurns.forceThreshold());
        }
    }

    // ── Test helpers ────────────────────────────────────────────────────────────

    private static PostReasoningEvent postReasoning() {
        var response =
                new ModelResponse(
                        "resp-1",
                        List.of(new Content.TextContent("some response")),
                        new ModelResponse.Usage(100, 50, 0, 0),
                        ModelResponse.StopReason.END_TURN,
                        "claude-sonnet-4-20250514");
        return new PostReasoningEvent(response, false);
    }

    private static PostReasoningEvent postReasoningWithTool(String toolName) {
        var response =
                new ModelResponse(
                        "resp-1",
                        List.of(new Content.ToolUseContent("tool-1", toolName, Map.of())),
                        new ModelResponse.Usage(100, 50, 0, 0),
                        ModelResponse.StopReason.TOOL_USE,
                        "claude-sonnet-4-20250514");
        return new PostReasoningEvent(response, false);
    }

    private static PreReasoningEvent preReasoning(List<Msg> messages) {
        return new PreReasoningEvent(
                messages, ModelConfig.builder().model("test-model").build(), false);
    }

    private static ToolResultEvent toolResult(String toolName) {
        return new ToolResultEvent(
                toolName, ToolResult.success("tool-1", "ok"), Duration.ofMillis(100), true);
    }
}
