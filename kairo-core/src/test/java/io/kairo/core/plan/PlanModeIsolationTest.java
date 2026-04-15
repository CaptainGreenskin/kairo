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
package io.kairo.core.plan;

import static org.junit.jupiter.api.Assertions.*;

import io.kairo.api.plan.PlanFile;
import io.kairo.api.plan.PlanStatus;
import io.kairo.api.tool.*;
import io.kairo.core.tool.DefaultToolExecutor;
import io.kairo.core.tool.DefaultToolRegistry;
import io.kairo.core.tool.ToolHandler;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class PlanModeIsolationTest {

    private DefaultToolRegistry registry;
    private DefaultToolExecutor executor;

    private static final PermissionGuard ALLOW_ALL =
            new PermissionGuard() {
                @Override
                public Mono<Boolean> checkPermission(String toolName, Map<String, Object> input) {
                    return Mono.just(true);
                }

                @Override
                public void addDangerousPattern(String pattern) {}
            };

    @BeforeEach
    void setUp() {
        registry = new DefaultToolRegistry();
        executor = new DefaultToolExecutor(registry, ALLOW_ALL);
    }

    private void registerTool(String name, ToolSideEffect sideEffect, ToolHandler handler) {
        ToolDefinition def =
                new ToolDefinition(
                        name,
                        "test tool",
                        ToolCategory.GENERAL,
                        new JsonSchema("object", null, null, null),
                        handler.getClass(),
                        null,
                        sideEffect);
        registry.register(def);
        registry.registerInstance(name, handler);
    }

    private ToolHandler echoHandler(String id) {
        return input -> new ToolResult(id, "result-" + id, false, Map.of());
    }

    // ============== Plan Mode Restriction Tests ==============

    @Test
    void writeToolBlockedInPlanMode() {
        registerTool("write_file", ToolSideEffect.WRITE, echoHandler("write_file"));
        executor.setPlanMode(true);

        StepVerifier.create(executor.execute("write_file", Map.of()))
                .assertNext(
                        result -> {
                            assertTrue(result.isError());
                            assertTrue(result.content().contains("blocked in Plan Mode"));
                        })
                .verifyComplete();
    }

    @Test
    void systemChangeToolBlockedInPlanMode() {
        registerTool("bash", ToolSideEffect.SYSTEM_CHANGE, echoHandler("bash"));
        executor.setPlanMode(true);

        StepVerifier.create(executor.execute("bash", Map.of()))
                .assertNext(
                        result -> {
                            assertTrue(result.isError());
                            assertTrue(result.content().contains("blocked in Plan Mode"));
                        })
                .verifyComplete();
    }

    @Test
    void readOnlyToolAllowedInPlanMode() {
        registerTool("read_file", ToolSideEffect.READ_ONLY, echoHandler("read_file"));
        executor.setPlanMode(true);

        StepVerifier.create(executor.execute("read_file", Map.of()))
                .assertNext(
                        result -> {
                            assertFalse(result.isError());
                            assertEquals("result-read_file", result.content());
                        })
                .verifyComplete();
    }

    @Test
    void planModeOffAllowsAllTools() {
        registerTool("write_file", ToolSideEffect.WRITE, echoHandler("write_file"));
        registerTool("bash", ToolSideEffect.SYSTEM_CHANGE, echoHandler("bash"));
        executor.setToolPermission("bash", ToolPermission.ALLOWED);
        executor.setPlanMode(false);

        StepVerifier.create(executor.execute("write_file", Map.of()))
                .assertNext(result -> assertFalse(result.isError()))
                .verifyComplete();
        StepVerifier.create(executor.execute("bash", Map.of()))
                .assertNext(result -> assertFalse(result.isError()))
                .verifyComplete();
    }

    @Test
    void planModeViolationExceptionHasToolName() {
        var ex = new PlanModeViolationException("Tool 'bash' blocked", "bash");
        assertEquals("bash", ex.getToolName());
        assertTrue(ex.getMessage().contains("bash"));
    }

    @Test
    void planModeViolationExceptionWithNullToolName() {
        var ex = new PlanModeViolationException("blocked");
        assertNull(ex.getToolName());
        assertEquals("blocked", ex.getMessage());
    }

    @Test
    void togglePlanModeOnAndOff() {
        registerTool("write_file", ToolSideEffect.WRITE, echoHandler("write_file"));

        assertFalse(executor.isPlanMode());

        executor.setPlanMode(true);
        assertTrue(executor.isPlanMode());

        // Write blocked
        StepVerifier.create(executor.execute("write_file", Map.of()))
                .assertNext(result -> assertTrue(result.isError()))
                .verifyComplete();

        executor.setPlanMode(false);
        assertFalse(executor.isPlanMode());

        // Write allowed again
        StepVerifier.create(executor.execute("write_file", Map.of()))
                .assertNext(result -> assertFalse(result.isError()))
                .verifyComplete();
    }

    // ============== PlanFileManager Tests ==============

    @Test
    void planFileManagerCreatesPlan(@TempDir Path tempDir) {
        var manager = new PlanFileManager(tempDir);
        PlanFile plan = manager.createPlan("My Plan");

        assertNotNull(plan);
        assertNotNull(plan.id());
        assertEquals("My Plan", plan.name());
        assertEquals(PlanStatus.DRAFT, plan.status());
        assertNotNull(plan.createdAt());
    }

    @Test
    void planFileManagerListsPlans(@TempDir Path tempDir) {
        var manager = new PlanFileManager(tempDir);
        manager.createPlan("Plan A");
        manager.createPlan("Plan B");

        List<PlanFile> plans = manager.listPlans();
        assertEquals(2, plans.size());
    }

    @Test
    void planFileManagerUpdatesPlanStatus(@TempDir Path tempDir) {
        var manager = new PlanFileManager(tempDir);
        PlanFile plan = manager.createPlan("Update Test");

        PlanFile updated =
                manager.updatePlan(plan.id(), "Step 1: do something", PlanStatus.APPROVED);

        assertEquals(plan.id(), updated.id());
        assertEquals("Update Test", updated.name());
        assertEquals("Step 1: do something", updated.content());
        assertEquals(PlanStatus.APPROVED, updated.status());
    }

    @Test
    void planFileManagerDeletesPlan(@TempDir Path tempDir) {
        var manager = new PlanFileManager(tempDir);
        PlanFile plan = manager.createPlan("To Delete");

        assertTrue(manager.deletePlan(plan.id()));
        assertNull(manager.getPlan(plan.id()));
        assertEquals(0, manager.listPlans().size());
    }

    @Test
    void planFilePersistsAsMarkdownWithFrontMatter(@TempDir Path tempDir) throws IOException {
        var manager = new PlanFileManager(tempDir);
        PlanFile plan = manager.createPlan("Markdown Plan");
        manager.updatePlan(plan.id(), "# Plan content\n- Step 1", PlanStatus.EXECUTING);

        Path file = tempDir.resolve(".kairo").resolve("plans").resolve(plan.id() + ".md");
        assertTrue(Files.exists(file));

        String raw = Files.readString(file);
        assertTrue(raw.startsWith("---\n"), "Should start with YAML front-matter");
        assertTrue(raw.contains("id: " + plan.id()));
        assertTrue(raw.contains("name: Markdown Plan"));
        assertTrue(raw.contains("status: EXECUTING"));
        assertTrue(raw.contains("createdAt:"));
        // Verify end of front-matter
        int firstDelim = raw.indexOf("---");
        int secondDelim = raw.indexOf("---", firstDelim + 3);
        assertTrue(secondDelim > firstDelim);
        // Content after front-matter
        String content = raw.substring(secondDelim + 3);
        if (content.startsWith("\n")) content = content.substring(1);
        assertTrue(content.contains("# Plan content"));
    }

    @Test
    void planFileManagerHandlesMissingDirectory(@TempDir Path tempDir) {
        // Use a subdirectory that doesn't exist yet
        var manager = new PlanFileManager(tempDir.resolve("deeply/nested"));
        PlanFile plan = manager.createPlan("Nested Plan");
        assertNotNull(plan);

        // Verify it was created
        PlanFile retrieved = manager.getPlan(plan.id());
        assertNotNull(retrieved);
        assertEquals("Nested Plan", retrieved.name());
    }

    @Test
    void planFileManagerGetNonexistentReturnsNull(@TempDir Path tempDir) {
        var manager = new PlanFileManager(tempDir);
        assertNull(manager.getPlan("nonexistent"));
    }

    @Test
    void planFileManagerUpdateNonexistentThrows(@TempDir Path tempDir) {
        var manager = new PlanFileManager(tempDir);
        assertThrows(
                IllegalArgumentException.class,
                () -> manager.updatePlan("nonexistent", "content", PlanStatus.DRAFT));
    }

    @Test
    void planFileManagerDeleteNonexistentReturnsFalse(@TempDir Path tempDir) {
        var manager = new PlanFileManager(tempDir);
        assertFalse(manager.deletePlan("nonexistent"));
    }
}
