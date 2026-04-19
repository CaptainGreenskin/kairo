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

import io.kairo.api.plan.PlanStatus;
import io.kairo.api.tool.ToolResult;
import io.kairo.core.plan.PlanFileManager;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ListPlansToolTest {

    @TempDir Path tempDir;

    private ListPlansTool tool;
    private PlanFileManager planFileManager;

    @BeforeEach
    void setUp() {
        tool = new ListPlansTool();
        planFileManager = new PlanFileManager(tempDir);
    }

    @Test
    void noPlanFileManagerConfigured() {
        ToolResult result = tool.execute(Map.of());
        assertTrue(result.isError());
        assertTrue(result.content().contains("PlanFileManager is not configured"));
    }

    @Test
    void emptyPlanList() {
        tool.setPlanFileManager(planFileManager);

        ToolResult result = tool.execute(Map.of());
        assertFalse(result.isError());
        assertTrue(result.content().contains("No plans found"));
    }

    @Test
    void singlePlanListed() {
        planFileManager.createPlan("Deploy to production");
        tool.setPlanFileManager(planFileManager);

        ToolResult result = tool.execute(Map.of());
        assertFalse(result.isError());
        assertTrue(result.content().contains("Deploy to production"));
        assertTrue(result.content().contains("DRAFT"));
        assertEquals(1, result.metadata().get("count"));
    }

    @Test
    void multiplePlansListed() {
        planFileManager.createPlan("Plan A");
        planFileManager.createPlan("Plan B");
        planFileManager.createPlan("Plan C");
        tool.setPlanFileManager(planFileManager);

        ToolResult result = tool.execute(Map.of());
        assertFalse(result.isError());
        assertTrue(result.content().contains("Plan A"));
        assertTrue(result.content().contains("Plan B"));
        assertTrue(result.content().contains("Plan C"));
        assertEquals(3, result.metadata().get("count"));
    }

    @Test
    void planStatusDisplayed() {
        var plan = planFileManager.createPlan("My Plan");
        planFileManager.updatePlan(plan.id(), "Updated content", PlanStatus.APPROVED);
        tool.setPlanFileManager(planFileManager);

        ToolResult result = tool.execute(Map.of());
        assertFalse(result.isError());
        assertTrue(result.content().contains("APPROVED"));
    }

    @Test
    void tableHeaderPresent() {
        planFileManager.createPlan("Some Plan");
        tool.setPlanFileManager(planFileManager);

        ToolResult result = tool.execute(Map.of());
        assertFalse(result.isError());
        assertTrue(result.content().contains("ID"));
        assertTrue(result.content().contains("Name"));
        assertTrue(result.content().contains("Status"));
        assertTrue(result.content().contains("Created"));
    }

    @Test
    void longPlanNameTruncated() {
        planFileManager.createPlan("This is a very long plan name that should be truncated");
        tool.setPlanFileManager(planFileManager);

        ToolResult result = tool.execute(Map.of());
        assertFalse(result.isError());
        // The truncated name should contain "..."
        assertTrue(result.content().contains("..."));
    }

    @Test
    void plansSortedNewestFirst() throws InterruptedException {
        planFileManager.createPlan("Old Plan");
        // Small delay to ensure different timestamps
        Thread.sleep(50);
        planFileManager.createPlan("New Plan");
        tool.setPlanFileManager(planFileManager);

        ToolResult result = tool.execute(Map.of());
        assertFalse(result.isError());
        String content = result.content();
        int newIndex = content.indexOf("New Plan");
        int oldIndex = content.indexOf("Old Plan");
        assertTrue(newIndex < oldIndex, "Newer plan should appear before older plan");
    }

    @Test
    void resultMetadataContainsCount() {
        planFileManager.createPlan("Plan 1");
        planFileManager.createPlan("Plan 2");
        tool.setPlanFileManager(planFileManager);

        ToolResult result = tool.execute(Map.of());
        assertEquals(2, result.metadata().get("count"));
    }
}
