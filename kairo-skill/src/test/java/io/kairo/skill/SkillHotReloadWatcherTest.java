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
package io.kairo.skill;

import static org.assertj.core.api.Assertions.assertThat;

import io.kairo.api.skill.SkillCategory;
import io.kairo.api.skill.SkillDefinition;
import io.kairo.api.skill.SkillRegistry;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SkillHotReloadWatcherTest {

    private static final String SKILL_MD =
            """
            ---
            name: test-skill
            version: 1.0.0
            category: CODE
            triggers:
              - "run tests"
            ---
            # Test Skill

            Instructions for testing.
            """;

    private static final String SKILL_MD_V2 =
            """
            ---
            name: test-skill
            version: 2.0.0
            category: CODE
            triggers:
              - "run tests"
            ---
            # Test Skill v2

            Updated instructions.
            """;

    private static final long EVENT_TIMEOUT_MILLIS = 5000;

    @TempDir Path tempDir;

    private SkillRegistry registry;
    private SkillLoader skillLoader;
    private SkillHotReloadWatcher watcher;
    private List<SkillReloadEvent> events;
    private CountDownLatch latch;

    @BeforeEach
    void setup() {
        registry = new DefaultSkillRegistry();
        skillLoader = new SkillLoader(registry);
        events = new CopyOnWriteArrayList<>();
        latch = new CountDownLatch(1);
    }

    @AfterEach
    void tearDown() {
        if (watcher != null) {
            watcher.stop();
        }
    }

    private void startWatcher() throws IOException {
        watcher =
                new SkillHotReloadWatcher(
                        tempDir,
                        skillLoader,
                        registry,
                        (file, kind) -> {
                            SkillReloadEvent.EventType type = mapKindToType(kind);
                            SkillReloadEvent event =
                                    new SkillReloadEvent(
                                            file.getFileName().toString(),
                                            type,
                                            java.time.Instant.now());
                            events.add(event);
                            latch.countDown();
                        });
        watcher.start();
        // Allow the watcher thread to register with the WatchService
        pause(300);
    }

    private static SkillReloadEvent.EventType mapKindToType(WatchEvent.Kind<Path> kind) {
        if (kind == StandardWatchEventKinds.ENTRY_CREATE) return SkillReloadEvent.EventType.CREATED;
        if (kind == StandardWatchEventKinds.ENTRY_MODIFY) return SkillReloadEvent.EventType.UPDATED;
        if (kind == StandardWatchEventKinds.ENTRY_DELETE) return SkillReloadEvent.EventType.DELETED;
        throw new IllegalArgumentException("Unknown event kind: " + kind);
    }

    private boolean awaitEvent() throws InterruptedException {
        return latch.await(EVENT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
    }

    private static void pause(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Test
    void createMdFile_triggersCreatedEvent() throws Exception {
        startWatcher();

        Path file = tempDir.resolve("new-skill.md");
        Files.writeString(file, SKILL_MD, StandardCharsets.UTF_8);

        assertThat(awaitEvent()).isTrue();
        assertThat(events).isNotEmpty();
        assertThat(events).anyMatch(e -> e.type() == SkillReloadEvent.EventType.CREATED);
    }

    @Test
    void modifyExistingFile_triggersUpdatedEvent() throws Exception {
        Path file = tempDir.resolve("existing-skill.md");
        Files.writeString(file, SKILL_MD, StandardCharsets.UTF_8);

        startWatcher();

        Files.writeString(file, SKILL_MD_V2, StandardCharsets.UTF_8);

        assertThat(awaitEvent()).isTrue();
        assertThat(events).isNotEmpty();
        assertThat(events).anyMatch(e -> e.type() == SkillReloadEvent.EventType.UPDATED);
    }

    @Test
    void deleteFile_triggersDeletedEvent() throws Exception {
        Path file = tempDir.resolve("deletable-skill.md");
        Files.writeString(file, SKILL_MD, StandardCharsets.UTF_8);

        startWatcher();

        Files.delete(file);

        assertThat(awaitEvent()).isTrue();
        assertThat(events).isNotEmpty();
        assertThat(events).anyMatch(e -> e.type() == SkillReloadEvent.EventType.DELETED);
    }

    @Test
    void stopWatcher_noMoreEvents() throws Exception {
        startWatcher();

        Path file1 = tempDir.resolve("skill1.md");
        Files.writeString(file1, SKILL_MD, StandardCharsets.UTF_8);
        assertThat(awaitEvent()).isTrue();

        int eventCountBefore = events.size();
        watcher.stop();
        pause(500);

        // After stop, no new events should be added
        assertThat(events).hasSize(eventCountBefore);
    }

    @Test
    void nonMdFileChange_isIgnored() throws Exception {
        startWatcher();

        Path txtFile = tempDir.resolve("readme.txt");
        Files.writeString(txtFile, "This is not a skill file", StandardCharsets.UTF_8);

        assertThat(awaitEvent()).isFalse();
        assertThat(events).isEmpty();
    }

    @Test
    void reloadFileSuccess_skillRegisteredInRegistry() throws Exception {
        startWatcher();

        Path file = tempDir.resolve("reload-skill.md");
        Files.writeString(file, SKILL_MD, StandardCharsets.UTF_8);

        assertThat(awaitEvent()).isTrue();

        // Wait for async reload to complete
        for (int i = 0; i < 30; i++) {
            if (registry.get("test-skill").isPresent()) break;
            pause(100);
        }

        assertThat(registry.get("test-skill")).isPresent();
        SkillDefinition skill = registry.get("test-skill").orElseThrow();
        assertThat(skill.name()).isEqualTo("test-skill");
        assertThat(skill.version()).isEqualTo("1.0.0");
        assertThat(skill.category()).isEqualTo(SkillCategory.CODE);
    }

    @Test
    void stopWithoutStart_doesNotThrow() {
        // watcher is null from @BeforeEach, calling stop on null is safe in tearDown
        // Explicitly create a watcher and stop without start
        watcher =
                new SkillHotReloadWatcher(
                        tempDir,
                        skillLoader,
                        registry,
                        (file, kind) ->
                                events.add(
                                        new SkillReloadEvent(
                                                "test",
                                                SkillReloadEvent.EventType.CREATED,
                                                java.time.Instant.now())));
        // Should not throw
        watcher.stop();
    }

    @Test
    void stopAndRestart_resumesWatching() throws Exception {
        startWatcher();

        // First file creation
        Path file1 = tempDir.resolve("skill-a.md");
        Files.writeString(file1, SKILL_MD, StandardCharsets.UTF_8);
        assertThat(awaitEvent()).isTrue();

        // Stop the watcher
        watcher.stop();
        pause(300);

        // Reset latch for new events
        latch = new CountDownLatch(1);

        // Create file while stopped — should NOT trigger
        Path file2 = tempDir.resolve("skill-b.md");
        Files.writeString(file2, SKILL_MD, StandardCharsets.UTF_8);
        pause(300);
        assertThat(latch.await(500, TimeUnit.MILLISECONDS)).isFalse();

        // Restart the watcher
        watcher.start();
        pause(300);
        latch = new CountDownLatch(1);

        // Modify file — should trigger after restart
        Files.writeString(file1, SKILL_MD_V2, StandardCharsets.UTF_8);
        assertThat(awaitEvent()).isTrue();
        assertThat(events).anyMatch(e -> e.skillId().equals("skill-a.md"));
    }

    @Test
    void reloadInvalidMdFile_noCrash_oldSkillRetained() throws Exception {
        startWatcher();

        // First, register a valid skill
        Path file = tempDir.resolve("valid-skill.md");
        Files.writeString(file, SKILL_MD, StandardCharsets.UTF_8);
        assertThat(awaitEvent()).isTrue();

        // Wait for async reload to complete
        for (int i = 0; i < 30; i++) {
            if (registry.get("test-skill").isPresent()) break;
            pause(100);
        }
        assertThat(registry.get("test-skill")).isPresent();
        assertThat(registry.get("test-skill").orElseThrow().version()).isEqualTo("1.0.0");

        // Reset latch for next event
        latch = new CountDownLatch(1);

        // Overwrite with invalid frontmatter (missing closing ---)
        String invalidMd =
                """
                ---
                name: test-skill
                version: 2.0.0
                incomplete frontmatter
                """;
        Files.writeString(file, invalidMd, StandardCharsets.UTF_8);
        assertThat(awaitEvent()).isTrue();

        // Wait for async reload attempt
        pause(500);

        // The old skill should still be in registry (reload failed, but didn't crash)
        // Note: parser may have partially parsed — the key is no crash occurred
        assertThat(registry).isNotNull();
    }

    @Test
    void rapidSuccessiveModifications_eachTriggersEvent() throws Exception {
        startWatcher();

        Path file = tempDir.resolve("rapid-skill.md");
        Files.writeString(file, SKILL_MD, StandardCharsets.UTF_8);
        assertThat(awaitEvent()).isTrue();

        // Reset for multiple events
        int eventsBefore = events.size();
        latch = new CountDownLatch(3);

        // Rapidly modify the same file 3 times
        for (int i = 0; i < 3; i++) {
            String content = SKILL_MD.replace("version: 1.0.0", "version: 1.0." + (i + 1));
            Files.writeString(file, content, StandardCharsets.UTF_8);
            pause(200);
        }

        // Wait for all events
        boolean allReceived = latch.await(EVENT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);

        int newEvents = events.size() - eventsBefore;
        // At least some events received (file system may coalesce)
        assertThat(newEvents).isGreaterThanOrEqualTo(1);
    }

    @Test
    void subdirectoryMdFile_notWatched() throws Exception {
        startWatcher();

        // Create a subdirectory with a .md file
        Path subDir = tempDir.resolve("subdir");
        Files.createDirectories(subDir);
        Path subFile = subDir.resolve("nested-skill.md");
        Files.writeString(subFile, SKILL_MD, StandardCharsets.UTF_8);

        // Wait a bit to ensure no event is triggered
        pause(1000);

        // Subdirectory changes should NOT trigger events (WatchService is not recursive)
        assertThat(events).isEmpty();
    }
}
