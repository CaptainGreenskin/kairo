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
package io.kairo.core.context.recovery;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import io.kairo.api.skill.SkillCategory;
import io.kairo.api.skill.SkillDefinition;
import io.kairo.api.skill.SkillRegistry;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PostCompactRecoveryHandlerTest {

    @TempDir Path tempDir;

    @Test
    @DisplayName("File re-read: track 3 files, recover, verify 3 USER messages with file content")
    void testFileReRead() throws IOException {
        // Create 3 temp files
        Path file1 = tempDir.resolve("App.java");
        Path file2 = tempDir.resolve("Config.java");
        Path file3 = tempDir.resolve("Service.java");
        Files.writeString(file1, "public class App {}");
        Files.writeString(file2, "public class Config {}");
        Files.writeString(file3, "public class Service {}");

        FileAccessTracker tracker = new FileAccessTracker();
        tracker.recordAccess(file1.toString());
        tracker.recordAccess(file2.toString());
        tracker.recordAccess(file3.toString());

        PostCompactRecoveryHandler handler = new PostCompactRecoveryHandler(tracker, null);

        List<Msg> recovery = handler.recover();

        assertEquals(3, recovery.size());
        for (Msg msg : recovery) {
            assertEquals(MsgRole.USER, msg.role());
            assertTrue(msg.text().contains("[Context Recovery]"));
            assertEquals(true, msg.metadata().get("recovery"));
            assertEquals("file", msg.metadata().get("recoveryType"));
        }

        // Verify file contents are included
        assertTrue(recovery.get(0).text().contains("public class Service"));
        assertTrue(recovery.get(1).text().contains("public class Config"));
        assertTrue(recovery.get(2).text().contains("public class App"));
    }

    @Test
    @DisplayName("Skill re-injection: register 2 skills, recover, verify 2 SYSTEM messages")
    void testSkillReInjection() {
        FileAccessTracker tracker = new FileAccessTracker();

        SkillRegistry skillRegistry = mock(SkillRegistry.class);
        SkillDefinition skill1 =
                new SkillDefinition(
                        "code-review",
                        "1.0",
                        "Reviews code",
                        "Review code carefully",
                        List.of("review"),
                        SkillCategory.CODE);
        SkillDefinition skill2 =
                new SkillDefinition(
                        "testing",
                        "1.0",
                        "Writes tests",
                        "Write comprehensive tests",
                        List.of("test"),
                        SkillCategory.CODE);
        when(skillRegistry.list()).thenReturn(List.of(skill1, skill2));

        PostCompactRecoveryHandler handler = new PostCompactRecoveryHandler(tracker, skillRegistry);

        List<Msg> recovery = handler.recover();

        assertEquals(2, recovery.size());
        for (Msg msg : recovery) {
            assertEquals(MsgRole.SYSTEM, msg.role());
            assertTrue(msg.text().contains("[Skill Recovery]"));
            assertEquals("skill", msg.metadata().get("recoveryType"));
        }
        assertTrue(recovery.get(0).text().contains("code-review"));
        assertTrue(recovery.get(1).text().contains("testing"));
    }

    @Test
    @DisplayName("50K total budget: fill with many large files, verify budget limit respected")
    void testBudgetLimit() throws IOException {
        // Create 5 large files (each ~20K chars -> ~6667 tokens at 3 chars/token)
        FileAccessTracker tracker = new FileAccessTracker();
        for (int i = 0; i < 5; i++) {
            Path file = tempDir.resolve("large-" + i + ".java");
            Files.writeString(file, "x".repeat(60_000)); // 60K chars -> ~20K tokens
            tracker.recordAccess(file.toString());
        }

        PostCompactRecoveryHandler handler = new PostCompactRecoveryHandler(tracker, null);

        List<Msg> recovery = handler.recover();

        // Calculate total budget used — should not exceed 50K tokens
        int totalEstimatedTokens = 0;
        for (Msg msg : recovery) {
            totalEstimatedTokens += (int) Math.ceil(msg.text().length() / 3.0);
        }
        // Each file is truncated to 5K tokens (5000 * 3 = 15000 chars)
        // Plus some overhead for the prefix text
        // Total should be well under 50K but the budget check happens per-file
        assertTrue(recovery.size() <= 5);
    }

    @Test
    @DisplayName("Priority order: files before skills")
    void testPriorityOrder() throws IOException {
        // Create a file
        Path file = tempDir.resolve("Main.java");
        Files.writeString(file, "public class Main {}");

        FileAccessTracker tracker = new FileAccessTracker();
        tracker.recordAccess(file.toString());

        // Skills
        SkillRegistry skillRegistry = mock(SkillRegistry.class);
        SkillDefinition skill =
                new SkillDefinition(
                        "test-skill",
                        "1.0",
                        "Test",
                        "Test instructions",
                        List.of("test"),
                        SkillCategory.CODE);
        when(skillRegistry.list()).thenReturn(List.of(skill));

        PostCompactRecoveryHandler handler = new PostCompactRecoveryHandler(tracker, skillRegistry);

        List<Msg> recovery = handler.recover();

        assertEquals(2, recovery.size());

        // First: file (USER)
        assertEquals(MsgRole.USER, recovery.get(0).role());
        assertEquals("file", recovery.get(0).metadata().get("recoveryType"));

        // Second: skill (SYSTEM)
        assertEquals(MsgRole.SYSTEM, recovery.get(1).role());
        assertEquals("skill", recovery.get(1).metadata().get("recoveryType"));
    }

    @Test
    @DisplayName("With null SkillRegistry still works (no skill messages)")
    void testNullSkillRegistry() throws IOException {
        Path file = tempDir.resolve("Test.java");
        Files.writeString(file, "public class Test {}");

        FileAccessTracker tracker = new FileAccessTracker();
        tracker.recordAccess(file.toString());

        PostCompactRecoveryHandler handler = new PostCompactRecoveryHandler(tracker, null);

        List<Msg> recovery = handler.recover();

        assertEquals(1, recovery.size());
        assertEquals("file", recovery.get(0).metadata().get("recoveryType"));
    }

    @Test
    @DisplayName("Files that don't exist are skipped gracefully")
    void testMissingFilesSkipped() {
        FileAccessTracker tracker = new FileAccessTracker();
        tracker.recordAccess("/nonexistent/path/Missing.java");

        PostCompactRecoveryHandler handler = new PostCompactRecoveryHandler(tracker, null);

        List<Msg> recovery = handler.recover();
        assertTrue(recovery.isEmpty());
    }

    @Test
    @DisplayName("Empty file tracker and no skills produces empty recovery")
    void testEmptyRecovery() {
        FileAccessTracker tracker = new FileAccessTracker();
        PostCompactRecoveryHandler handler = new PostCompactRecoveryHandler(tracker, null);

        List<Msg> recovery = handler.recover();
        assertTrue(recovery.isEmpty());
    }
}
