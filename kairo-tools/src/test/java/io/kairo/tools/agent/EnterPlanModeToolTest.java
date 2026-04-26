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

import io.kairo.api.tool.Tool;
import io.kairo.api.tool.ToolCategory;
import io.kairo.api.tool.ToolResult;
import io.kairo.api.tool.ToolSideEffect;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EnterPlanModeToolTest {

    private EnterPlanModeTool tool;

    @BeforeEach
    void setUp() {
        tool = new EnterPlanModeTool();
    }

    @Test
    void toolAnnotationName() {
        Tool annotation = EnterPlanModeTool.class.getAnnotation(Tool.class);
        assertEquals("enter_plan_mode", annotation.name());
    }

    @Test
    void toolAnnotationCategory() {
        Tool annotation = EnterPlanModeTool.class.getAnnotation(Tool.class);
        assertEquals(ToolCategory.AGENT_AND_TASK, annotation.category());
    }

    @Test
    void toolAnnotationSideEffect() {
        Tool annotation = EnterPlanModeTool.class.getAnnotation(Tool.class);
        assertEquals(ToolSideEffect.READ_ONLY, annotation.sideEffect());
    }

    @Test
    void executeWithoutNameUsesDefault() {
        ToolResult result = tool.execute(Map.of());
        assertFalse(result.isError());
        assertTrue(result.content().contains("Entered Plan Mode"));
    }

    @Test
    void executeWithNameIncludesItInMode() {
        ToolResult result = tool.execute(Map.of("name", "My Plan"));
        assertFalse(result.isError());
        assertTrue(result.content().contains("Entered Plan Mode"));
    }

    @Test
    void executeWithoutPlanManagerHasNoPlanId() {
        ToolResult result = tool.execute(Map.of("name", "Test"));
        assertFalse(result.metadata().containsKey("planId"));
        assertEquals("plan", result.metadata().get("mode"));
    }

    @Test
    void executeResultContainsPlanMode() {
        ToolResult result = tool.execute(Map.of());
        assertEquals("plan", result.metadata().get("mode"));
    }
}
