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
import io.kairo.api.tool.ToolCallRequest;
import io.kairo.api.tool.ToolResult;
import io.kairo.api.tool.UserApprovalHandler;
import io.kairo.core.plan.PlanFileManager;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import reactor.core.publisher.Mono;

class ExitPlanModeToolTest {

    @TempDir Path tempDir;

    private ExitPlanModeTool tool;
    private PlanFileManager planFileManager;

    @BeforeEach
    void setUp() {
        tool = new ExitPlanModeTool();
        planFileManager = new PlanFileManager(tempDir);
    }

    @Test
    void missingOverviewParameter() {
        ToolResult result = tool.execute(Map.of());
        assertTrue(result.isError());
        assertTrue(result.content().contains("'overview' is required"));
    }

    @Test
    void blankOverviewParameter() {
        ToolResult result = tool.execute(Map.of("overview", "   "));
        assertTrue(result.isError());
        assertTrue(result.content().contains("'overview' is required"));
    }

    @Test
    void exitWithoutAnyDependencies() {
        ToolResult result = tool.execute(Map.of("overview", "Deploy to production"));
        assertFalse(result.isError());
        assertTrue(result.content().contains("Exited Plan Mode"));
        assertTrue(result.content().contains("Deploy to production"));
        assertEquals("execute", result.metadata().get("mode"));
    }

    @Test
    void exitWithoutToolExecutorDoesNotThrow() {
        // No tool executor set - should not throw NPE
        ToolResult result = tool.execute(Map.of("overview", "Deploy"));
        assertFalse(result.isError());
    }

    @Test
    void exitWithPlanIdButNoManager() {
        // planId present but no planFileManager set
        ToolResult result = tool.execute(Map.of("overview", "Deploy v2", "planId", "abc123"));

        assertFalse(result.isError());
    }

    @Test
    void exitWithPlanManagerButNoPlanId() {
        tool.setPlanFileManager(planFileManager);

        ToolResult result = tool.execute(Map.of("overview", "Deploy v2"));

        assertFalse(result.isError());
        // No exception even though planFileManager is set — just no plan to update
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
                                "Full plan content"));

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

        ToolResult result = tool.execute(Map.of("overview", "Deploy v2", "planId", "nonexistent"));

        // Should still succeed despite plan not found (caught IllegalArgumentException)
        assertFalse(result.isError());
        assertTrue(result.content().contains("Exited Plan Mode"));
    }

    @Test
    void exitWithApprovalApproved() {
        tool.setApprovalHandler(new StubApprovalHandler(ApprovalResult.allow()));

        ToolResult result = tool.execute(Map.of("overview", "Deploy v2"));

        assertFalse(result.isError());
        assertEquals("execute", result.metadata().get("mode"));
    }

    @Test
    void exitWithApprovalDenied() {
        tool.setApprovalHandler(new StubApprovalHandler(ApprovalResult.denied("Not ready yet")));

        ToolResult result = tool.execute(Map.of("overview", "Deploy v2"));

        assertFalse(result.isError());
        assertTrue(result.content().contains("Plan exit denied"));
        assertTrue(result.content().contains("Not ready yet"));
        assertTrue(result.content().contains("Still in Plan Mode"));
        assertEquals("plan", result.metadata().get("mode"));
    }

    @Test
    void exitWithApprovalNullResult() {
        tool.setApprovalHandler(new StubApprovalHandler(null));

        ToolResult result = tool.execute(Map.of("overview", "Deploy v2"));

        assertFalse(result.isError());
        // null result is treated as not denied, so exits normally
        assertEquals("execute", result.metadata().get("mode"));
    }

    @Test
    void planContentDefaultsToOverview() {
        tool.setPlanFileManager(planFileManager);
        var plan = planFileManager.createPlan("Test Plan");

        tool.execute(Map.of("overview", "Deploy overview", "planId", plan.id()));

        var updated = planFileManager.getPlan(plan.id());
        assertNotNull(updated);
        assertEquals("Deploy overview", updated.content());
    }

    @Test
    void successResultFormat() {
        ToolResult result = tool.execute(Map.of("overview", "Do things"));
        assertNull(result.toolUseId());
        assertFalse(result.isError());
        assertEquals("execute", result.metadata().get("mode"));
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
}
