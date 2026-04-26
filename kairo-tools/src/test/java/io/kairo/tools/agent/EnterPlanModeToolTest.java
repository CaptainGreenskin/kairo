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

import static org.assertj.core.api.Assertions.assertThat;

import io.kairo.api.tool.Tool;
import io.kairo.api.tool.ToolResult;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EnterPlanModeToolTest {

    @Test
    void toolAnnotationNameIsEnterPlanMode() {
        Tool annotation = EnterPlanModeTool.class.getAnnotation(Tool.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.name()).isEqualTo("enter_plan_mode");
    }

    @Test
    void executeWithNoInputReturnsEnteredPlanMode() {
        EnterPlanModeTool tool = new EnterPlanModeTool();
        ToolResult result = tool.execute(Map.of());
        assertThat(result.content()).startsWith("Entered Plan Mode");
    }

    @Test
    void executeIsNotError() {
        EnterPlanModeTool tool = new EnterPlanModeTool();
        ToolResult result = tool.execute(Map.of());
        assertThat(result.isError()).isFalse();
    }

    @Test
    void executeWithNameInputReturnsEnteredPlanMode() {
        EnterPlanModeTool tool = new EnterPlanModeTool();
        ToolResult result = tool.execute(Map.of("name", "My Plan"));
        assertThat(result.content()).startsWith("Entered Plan Mode");
    }

    @Test
    void metadataContainsPlanModeFlag() {
        EnterPlanModeTool tool = new EnterPlanModeTool();
        ToolResult result = tool.execute(Map.of());
        assertThat(result.metadata()).containsEntry("mode", "plan");
    }
}
