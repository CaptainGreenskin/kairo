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
package io.kairo.tools.skill;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.kairo.api.exception.ToolException;
import io.kairo.api.skill.SkillCategory;
import io.kairo.api.skill.SkillDefinition;
import io.kairo.api.skill.SkillRegistry;
import io.kairo.api.tool.ToolResult;
import io.kairo.core.skill.SkillChangeHistory;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SkillManageToolTest {

    @TempDir Path tempDir;

    private SkillRegistry registry;
    private SkillChangeHistory changeHistory;
    private SkillManageTool tool;

    private static final String VALID_SKILL_CONTENT =
            """
            ---
            name: test-skill
            version: 1.0.0
            category: CODE
            triggers:
              - "/test"
            ---
            # Test Skill

            This is a test skill for unit testing.

            ## Instructions
            Do testing things.
            """;

    @BeforeEach
    void setUp() {
        registry = mock(SkillRegistry.class);
        changeHistory = new SkillChangeHistory(tempDir.resolve("history"));
        tool = new SkillManageTool(registry, changeHistory, List.of(tempDir.toString()), false);
    }

    @Test
    void createSkill() throws IOException {
        when(registry.get("test-skill")).thenReturn(Optional.empty());

        ToolResult result =
                tool.execute(
                        Map.of(
                                "operation",
                                "create",
                                "name",
                                "test-skill",
                                "content",
                                VALID_SKILL_CONTENT));

        assertFalse(result.isError(), result.content());
        assertTrue(result.content().contains("created"));
        assertTrue(result.content().contains("test-skill"));

        // Verify file was written
        Path skillFile = tempDir.resolve("test-skill.md");
        assertTrue(Files.exists(skillFile));
        String written = Files.readString(skillFile, StandardCharsets.UTF_8);
        assertTrue(written.contains("name: test-skill"));

        // Verify registered
        verify(registry).register(any(SkillDefinition.class));

        // Verify history recorded
        var history = changeHistory.getHistory("test-skill");
        assertEquals(1, history.size());
        assertEquals("create", history.get(0).operation());
    }

    @Test
    void editSkill() throws IOException {
        SkillDefinition existing =
                new SkillDefinition(
                        "test-skill",
                        "1.0.0",
                        "desc",
                        "old instructions",
                        List.of("/test"),
                        SkillCategory.CODE);
        when(registry.get("test-skill")).thenReturn(Optional.of(existing));

        ToolResult result =
                tool.execute(
                        Map.of(
                                "operation",
                                "edit",
                                "name",
                                "test-skill",
                                "content",
                                VALID_SKILL_CONTENT));

        assertFalse(result.isError(), result.content());
        assertTrue(result.content().contains("updated"));

        // Verify file was written
        Path skillFile = tempDir.resolve("test-skill.md");
        assertTrue(Files.exists(skillFile));

        // Verify re-registered
        verify(registry).register(any(SkillDefinition.class));

        // Verify history recorded with old content
        var history = changeHistory.getHistory("test-skill");
        assertEquals(1, history.size());
        assertEquals("edit", history.get(0).operation());
        assertFalse(history.get(0).content().isEmpty());
    }

    @Test
    void deleteSkill() {
        SkillDefinition existing =
                new SkillDefinition(
                        "test-skill",
                        "1.0.0",
                        "desc",
                        "instructions",
                        List.of("/test"),
                        SkillCategory.CODE);
        when(registry.get("test-skill")).thenReturn(Optional.of(existing));

        ToolResult result = tool.execute(Map.of("operation", "delete", "name", "test-skill"));

        assertFalse(result.isError(), result.content());
        assertTrue(result.content().contains("deleted"));

        // Verify unregistered
        verify(registry).unregister("test-skill");

        // Verify history recorded
        var history = changeHistory.getHistory("test-skill");
        assertEquals(1, history.size());
        assertEquals("delete", history.get(0).operation());
    }

    @Test
    void readonlyRejectsAllOperations() {
        SkillManageTool readonlyTool =
                new SkillManageTool(registry, changeHistory, List.of(tempDir.toString()), true);

        ToolResult createResult =
                readonlyTool.execute(
                        Map.of("operation", "create", "name", "x", "content", VALID_SKILL_CONTENT));
        assertTrue(createResult.isError());
        assertTrue(createResult.content().contains("readonly"));

        ToolResult editResult =
                readonlyTool.execute(
                        Map.of("operation", "edit", "name", "x", "content", VALID_SKILL_CONTENT));
        assertTrue(editResult.isError());
        assertTrue(editResult.content().contains("readonly"));

        ToolResult deleteResult = readonlyTool.execute(Map.of("operation", "delete", "name", "x"));
        assertTrue(deleteResult.isError());
        assertTrue(deleteResult.content().contains("readonly"));
    }

    @Test
    void createDuplicateNameRejected() {
        SkillDefinition existing =
                new SkillDefinition(
                        "test-skill", "1.0.0", "desc", "inst", List.of(), SkillCategory.CODE);
        when(registry.get("test-skill")).thenReturn(Optional.of(existing));

        ToolResult result =
                tool.execute(
                        Map.of(
                                "operation",
                                "create",
                                "name",
                                "test-skill",
                                "content",
                                VALID_SKILL_CONTENT));

        assertTrue(result.isError());
        assertTrue(result.content().contains("already exists"));
    }

    @Test
    void editNonExistentSkillRejected() {
        when(registry.get("nonexistent")).thenReturn(Optional.empty());

        ToolResult result =
                tool.execute(
                        Map.of(
                                "operation",
                                "edit",
                                "name",
                                "nonexistent",
                                "content",
                                VALID_SKILL_CONTENT));

        assertTrue(result.isError());
        assertTrue(result.content().contains("does not exist"));
    }

    @Test
    void deleteNonExistentSkillRejected() {
        when(registry.get("nonexistent")).thenReturn(Optional.empty());

        ToolResult result = tool.execute(Map.of("operation", "delete", "name", "nonexistent"));

        assertTrue(result.isError());
        assertTrue(result.content().contains("does not exist"));
    }

    @Test
    void noWritablePathThrows() {
        SkillManageTool classpathOnlyTool =
                new SkillManageTool(registry, changeHistory, List.of("classpath:skills"), false);
        when(registry.get("test-skill")).thenReturn(Optional.empty());

        assertThrows(
                ToolException.class,
                () ->
                        classpathOnlyTool.execute(
                                Map.of(
                                        "operation",
                                        "create",
                                        "name",
                                        "test-skill",
                                        "content",
                                        VALID_SKILL_CONTENT)));
    }

    @Test
    void writablePathSelectsHighestPriority() {
        Path lowPriority = tempDir.resolve("low");
        Path highPriority = tempDir.resolve("high");
        lowPriority.toFile().mkdirs();
        highPriority.toFile().mkdirs();

        SkillManageTool multiPathTool =
                new SkillManageTool(
                        registry,
                        changeHistory,
                        List.of(
                                "classpath:skills",
                                lowPriority.toString(),
                                highPriority.toString()),
                        false);

        // highPriority is last non-classpath path, so it should be selected
        Path resolved = multiPathTool.resolveWritablePath();
        assertEquals(highPriority, resolved);
    }

    @Test
    void invalidContentRejected() {
        when(registry.get("bad-skill")).thenReturn(Optional.empty());

        ToolResult result =
                tool.execute(
                        Map.of(
                                "operation",
                                "create",
                                "name",
                                "bad-skill",
                                "content",
                                "not valid markdown"));

        assertTrue(result.isError());
        assertTrue(result.content().contains("Invalid skill content"));
    }

    @Test
    void missingOperationRejected() {
        ToolResult result = tool.execute(Map.of("name", "x"));
        assertTrue(result.isError());
        assertTrue(result.content().contains("'operation' is required"));
    }

    @Test
    void unknownOperationRejected() {
        ToolResult result = tool.execute(Map.of("operation", "rename", "name", "x"));
        assertTrue(result.isError());
        assertTrue(result.content().contains("Unknown operation"));
    }
}
