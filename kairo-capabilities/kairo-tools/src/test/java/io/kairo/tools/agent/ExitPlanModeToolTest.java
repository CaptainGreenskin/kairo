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
package io.kairo.tools.agent;

import static org.junit.jupiter.api.Assertions.*;

import io.kairo.api.tool.ApprovalResult;
import io.kairo.api.tool.Tool;
import io.kairo.api.tool.ToolCallRequest;
import io.kairo.api.tool.ToolContext;
import io.kairo.api.tool.ToolResult;
import io.kairo.api.tool.UserApprovalHandler;
import io.kairo.core.plan.PlanFileManager;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

class ExitPlanModeToolTest {

    private static final ToolContext CTX = new ToolContext("agent-1", "sess-1", Map.of());

    @TempDir Path tempDir;

    private ExitPlanModeTool tool;
    private PlanFileManager planFileManager;

    @BeforeEach
    void setUp() {
        tool = new ExitPlanModeTool();
        planFileManager = new PlanFileManager(tempDir);
    }

    /** Minimal valid input — overview + plan_content are both required by the schema. */
    private static Map<String, Object> base(String overview) {
        return Map.of("overview", overview, "plan_content", "## Plan\n\nDo the work.");
    }

    @Test
    void missingOverviewParameter() {
        ToolResult result = tool.execute(Map.of(), CTX).block();
        assertTrue(result.isError());
        assertTrue(result.content().contains("'overview' is required"));
    }

    @Test
    void blankOverviewParameter() {
        ToolResult result =
                tool.execute(Map.of("overview", "   ", "plan_content", "## Plan\n\nDoit"), CTX)
                        .block();
        assertTrue(result.isError());
        assertTrue(result.content().contains("'overview' is required"));
    }

    @Test
    void missingPlanContentParameter() {
        ToolResult result = tool.execute(Map.of("overview", "Deploy"), CTX).block();
        assertTrue(result.isError());
        assertTrue(result.content().contains("'plan_content' is required"));
    }

    @Test
    void blankPlanContentParameter() {
        ToolResult result =
                tool.execute(Map.of("overview", "Deploy", "plan_content", "   "), CTX).block();
        assertTrue(result.isError());
        assertTrue(result.content().contains("'plan_content' is required"));
    }

    @Test
    void exitWithoutAnyDependencies() {
        ToolResult result = tool.execute(base("Deploy to production"), CTX).block();
        assertFalse(result.isError());
        assertTrue(result.content().contains("Exited Plan Mode"));
        assertTrue(result.content().contains("Deploy to production"));
        assertEquals("execute", result.metadata().get("mode"));
    }

    @Test
    void successMessagePointsAtVerifyExecutionTool() {
        // M-PlanVerify soft-link: the exit message must direct the agent at
        // verify_execution as the final step. Without this nudge the model
        // tends to declare "done" the moment the last todo flips to complete,
        // skipping the cheapest build/test sanity check.
        ToolResult result = tool.execute(base("Anything"), CTX).block();
        assertFalse(result.isError());
        assertTrue(result.content().contains("verify_execution"), () -> "got: " + result.content());
        assertTrue(
                result.content().contains("BEFORE declaring the task done"),
                () -> "got: " + result.content());
    }

    @Test
    void exitWithoutToolExecutorDoesNotThrow() {
        ToolResult result = tool.execute(base("Deploy"), CTX).block();
        assertFalse(result.isError());
    }

    @Test
    void exitWithPlanIdButNoManager() {
        Map<String, Object> input = new java.util.HashMap<>(base("Deploy v2"));
        input.put("planId", "abc123");
        ToolResult result = tool.execute(input, CTX).block();
        assertFalse(result.isError());
    }

    @Test
    void exitWithPlanManagerButNoPlanId() {
        tool.setPlanFileManager(planFileManager);
        ToolResult result = tool.execute(base("Deploy v2"), CTX).block();
        assertFalse(result.isError());
    }

    @Test
    void exitWithApprovedPlan() {
        tool.setPlanFileManager(planFileManager);
        // Create a plan first so updatePlan can succeed
        var plan = planFileManager.createPlan("Test Plan");

        ToolResult result =
                tool.execute(
                                Map.of(
                                        "overview",
                                        "Deploy v2",
                                        "planId",
                                        plan.id(),
                                        "plan_content",
                                        "Full plan content"),
                                CTX)
                        .block();

        assertFalse(result.isError());
        // Verify the plan was updated
        var updated = planFileManager.getPlan(plan.id());
        assertNotNull(updated);
        assertEquals("Full plan content", updated.content());
        assertEquals(io.kairo.api.plan.PlanStatus.APPROVED, updated.status());
    }

    @Test
    void exitWithPlanNotFoundContinues() {
        tool.setPlanFileManager(planFileManager);

        Map<String, Object> input = new java.util.HashMap<>(base("Deploy v2"));
        input.put("planId", "nonexistent");
        ToolResult result = tool.execute(input, CTX).block();

        // Should still succeed despite plan not found (caught IllegalArgumentException)
        assertFalse(result.isError());
        assertTrue(result.content().contains("Exited Plan Mode"));
    }

    @Test
    void exitWithApprovalApproved() {
        tool.setApprovalHandler(new StubApprovalHandler(ApprovalResult.allow()));

        ToolResult result = tool.execute(base("Deploy v2"), CTX).block();

        assertFalse(result.isError());
        assertEquals("execute", result.metadata().get("mode"));
    }

    @Test
    void exitWithApprovalDenied() {
        tool.setApprovalHandler(new StubApprovalHandler(ApprovalResult.denied("Not ready yet")));

        ToolResult result = tool.execute(base("Deploy v2"), CTX).block();

        assertFalse(result.isError());
        assertTrue(result.content().contains("Plan exit denied"));
        assertTrue(result.content().contains("Not ready yet"));
        assertTrue(result.content().contains("Still in Plan Mode"));
        assertEquals("plan", result.metadata().get("mode"));
    }

    @Test
    void exitWithApprovalNullResult() {
        tool.setApprovalHandler(new StubApprovalHandler(null));

        ToolResult result = tool.execute(base("Deploy v2"), CTX).block();

        assertFalse(result.isError());
        // null result is treated as not denied, so exits normally
        assertEquals("execute", result.metadata().get("mode"));
    }

    @Test
    void planContentPersistsToFile() {
        tool.setPlanFileManager(planFileManager);
        var plan = planFileManager.createPlan("Test Plan");

        tool.execute(
                        Map.of(
                                "overview",
                                "Deploy overview",
                                "planId",
                                plan.id(),
                                "plan_content",
                                "## Goal\n\nMarkdown body the user reviews"),
                        CTX)
                .block();

        var updated = planFileManager.getPlan(plan.id());
        assertNotNull(updated);
        assertEquals("## Goal\n\nMarkdown body the user reviews", updated.content());
    }

    @Test
    void successResultFormat() {
        ToolResult result = tool.execute(base("Do things"), CTX).block();
        assertNull(result.toolUseId());
        assertFalse(result.isError());
        assertEquals("execute", result.metadata().get("mode"));
    }

    // ---------------------------------------------------------------------------------
    // Long-task / cooperative-cancellation tests
    //
    // The bug we're guarding against: the agent calls exit_plan_mode → block()s on user
    // approval. Without a long per-tool timeout (and without an external cancel path),
    // the 120s DefaultToolExecutor timeout would interrupt the wait and surface
    // InterruptedException as "Plan submission failed".
    //
    // The pattern below tests *the cancellation mechanism itself* without actually
    // sleeping for minutes. We use a Sinks.One that an external thread resolves with
    // denied (mirroring WebSocketApprovalHandler.cancelAll()), and assert that
    // tool.execute() unwinds within a budget of 1s with a clean denied result.
    // ---------------------------------------------------------------------------------

    @Test
    void exitPlanModeAnnotationDeclaresLongTimeout() {
        // Static config assertion: the @Tool annotation declares a timeout that exceeds
        // the DefaultToolExecutor's 120s default. If someone removes this, the test fails
        // before they cause a regression in production.
        Tool annotation = ExitPlanModeTool.class.getAnnotation(Tool.class);
        assertNotNull(annotation, "ExitPlanModeTool must be annotated with @Tool");
        assertTrue(
                annotation.timeoutSeconds() >= 3600,
                "exit_plan_mode timeoutSeconds must be >=1h to survive human review;"
                        + " got "
                        + annotation.timeoutSeconds());
    }

    @Test
    void externalSinkResolutionUnwindsBlockCleanly() throws Exception {
        // Simulates the production unwind: the user clicks Stop while exit_plan_mode is
        // awaiting approval. AgentService.stopAgent() calls approvalHandler.cancelAll(),
        // which resolves the Sinks.One with denied. block() then returns within ms instead
        // of throwing InterruptedException.
        Sinks.One<ApprovalResult> sink = Sinks.one();
        tool.setApprovalHandler(new SinkBackedApprovalHandler(sink));

        // Schedule the "user pressed Stop" event 50ms after exec starts.
        CompletableFuture<Void> canceller =
                CompletableFuture.runAsync(
                        () -> {
                            try {
                                Thread.sleep(50);
                            } catch (InterruptedException ignored) {
                                Thread.currentThread().interrupt();
                            }
                            sink.tryEmitValue(ApprovalResult.denied("Session terminated"));
                        });

        long start = System.nanoTime();
        ToolResult result = tool.execute(base("Long-running plan"), CTX).block();
        Duration elapsed = Duration.ofNanos(System.nanoTime() - start);
        canceller.get(2, java.util.concurrent.TimeUnit.SECONDS);

        assertFalse(
                result.isError(), "Cancellation must produce a clean denied result, not an error");
        assertEquals("plan", result.metadata().get("mode"));
        assertTrue(result.content().contains("Session terminated"));
        assertTrue(result.content().contains("Still in Plan Mode"));
        assertTrue(
                elapsed.toMillis() < 1000,
                "Cancellation should unwind within 1s; took " + elapsed.toMillis() + "ms");
    }

    /** Stub UserApprovalHandler for testing without Mockito. */
    private static class StubApprovalHandler implements UserApprovalHandler {
        private final ApprovalResult result;

        StubApprovalHandler(ApprovalResult result) {
            this.result = result;
        }

        @Override
        public Mono<ApprovalResult> requestApproval(ToolCallRequest request) {
            if (result == null) {
                return Mono.empty();
            }
            return Mono.just(result);
        }
    }

    /**
     * Hands out a Mono backed by an externally-controlled Sinks.One. Mirrors the production pattern
     * in WebSocketApprovalHandler (one sink per pending approval, resolved out of band by the
     * WebSocket thread or cancelAll()).
     */
    private static class SinkBackedApprovalHandler implements UserApprovalHandler {
        private final Sinks.One<ApprovalResult> sink;

        SinkBackedApprovalHandler(Sinks.One<ApprovalResult> sink) {
            this.sink = sink;
        }

        @Override
        public Mono<ApprovalResult> requestApproval(ToolCallRequest request) {
            return sink.asMono();
        }
    }
}
