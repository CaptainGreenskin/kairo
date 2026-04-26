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

import io.kairo.api.tool.ToolResult;
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
    void noNameParameterUsesUntitledPlan() {
        ToolResult result = tool.execute(Map.of());
        assertFalse(result.isError());
        assertTrue(result.content().contains("Entered Plan Mode"));
    }

    @Test
    void nameParameterIsReflectedInMessage() {
        ToolResult result = tool.execute(Map.of("name", "My Feature Plan"));
        assertFalse(result.isError());
    }

    @Test
    void metadataContainsModeIsPlan() {
        ToolResult result = tool.execute(Map.of());
        assertFalse(result.isError());
        assertEquals("plan", result.metadata().get("mode"));
    }

    @Test
    void withoutPlanFileManagerNoPlanIdInMetadata() {
        ToolResult result = tool.execute(Map.of());
        assertFalse(result.metadata().containsKey("planId"));
    }

    @Test
    void withoutToolExecutorDoesNotThrow() {
        assertDoesNotThrow(() -> tool.execute(Map.of("name", "test")));
    }

    @Test
    void resultIsNotError() {
        ToolResult result = tool.execute(Map.of());
        assertFalse(result.isError());
    }

    @Test
    void contentDescribesPlanModeTools() {
        ToolResult result = tool.execute(Map.of());
        String content = result.content();
        assertTrue(content.contains("Read"));
        assertTrue(content.contains("Grep"));
        assertTrue(content.contains("Glob"));
    }
}
