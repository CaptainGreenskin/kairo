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

import io.kairo.core.plan.PlanFileManager;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EnterPlanModeToolTest {

    @TempDir Path workDir;

    private EnterPlanModeTool tool() {
        return new EnterPlanModeTool();
    }

    private PlanFileManager planFileManager() {
        return new PlanFileManager(workDir);
    }

    @Test
    void withoutDependenciesEntersPlanMode() {
        var result = tool().execute(Map.of());
        assertThat(result.isError()).isFalse();
        assertThat(result.content()).contains("Entered Plan Mode");
    }

    @Test
    void metadataModeIsPlan() {
        var result = tool().execute(Map.of());
        assertThat(result.metadata()).containsEntry("mode", "plan");
    }

    @Test
    void noPlanIdInMetadataWhenNoPlanFileManager() {
        var result = tool().execute(Map.of());
        assertThat(result.metadata()).doesNotContainKey("planId");
    }

    @Test
    void noNameParameterCreatesUntitledPlan() {
        var t = tool();
        t.setPlanFileManager(planFileManager());
        var result = t.execute(Map.of());

        assertThat(result.isError()).isFalse();
        assertThat(result.metadata()).containsKey("planId");
        // plan file should exist on disk
        var plansDir = workDir.resolve(".kairo").resolve("plans");
        assertThat(plansDir.toFile().listFiles()).isNotEmpty();
    }

    @Test
    void nameParameterIsUsedForPlan() {
        var t = tool();
        t.setPlanFileManager(planFileManager());
        var result = t.execute(Map.of("name", "Refactor Auth"));

        assertThat(result.isError()).isFalse();
        assertThat(result.metadata()).containsKey("planId");
        // plan file content should contain the plan name
        var plansDir = workDir.resolve(".kairo").resolve("plans");
        var files = plansDir.toFile().listFiles();
        assertThat(files).isNotNull().hasSize(1);
        var content = files[0].toPath().toFile();
        assertThat(content).exists();
    }

    @Test
    void planIdAppearsInMetadataWhenManagerIsSet() {
        var t = tool();
        t.setPlanFileManager(planFileManager());
        var result = t.execute(Map.of("name", "Test Plan"));

        String planId = (String) result.metadata().get("planId");
        assertThat(planId).isNotNull().isNotBlank();
        assertThat(result.metadata()).containsEntry("mode", "plan");
    }

    @Test
    void contentMentionsBlockedWriteTools() {
        var result = tool().execute(Map.of());
        assertThat(result.content()).containsIgnoringCase("write");
    }
}
