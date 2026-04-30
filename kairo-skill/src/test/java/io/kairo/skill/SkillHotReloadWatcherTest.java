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
}
