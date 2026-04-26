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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kairo.skill.SkillChangeHistory.HistoryEntry;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SkillChangeHistoryTest {

    @TempDir Path tempDir;

    @Test
    void recordAndRetrieveSingleChange() {
        var history = new SkillChangeHistory(tempDir.resolve(".history"));

        history.recordChange("code-review", "create", "sre-agent", "---\nname: code-review\n---");

        List<HistoryEntry> entries = history.getHistory("code-review");
        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).operation()).isEqualTo("create");
        assertThat(entries.get(0).agentName()).isEqualTo("sre-agent");
        assertThat(entries.get(0).content()).isEqualTo("---\nname: code-review\n---");
        assertThat(entries.get(0).timestamp()).isNotNull();
    }

    @Test
    void multipleChangesReturnedInOrder() {
        var history = new SkillChangeHistory(tempDir.resolve(".history"));

        history.recordChange("deploy", "create", "agent-a", "v1");
        history.recordChange("deploy", "edit", "agent-b", "v2");
        history.recordChange("deploy", "delete", "agent-c", "v3");

        List<HistoryEntry> entries = history.getHistory("deploy");
        assertThat(entries).hasSize(3);
        assertThat(entries.get(0).operation()).isEqualTo("create");
        assertThat(entries.get(1).operation()).isEqualTo("edit");
        assertThat(entries.get(2).operation()).isEqualTo("delete");
        // oldest first — timestamps should be non-decreasing
        assertThat(entries.get(0).timestamp()).isBeforeOrEqualTo(entries.get(1).timestamp());
        assertThat(entries.get(1).timestamp()).isBeforeOrEqualTo(entries.get(2).timestamp());
    }

    @Test
    void jsonlFormatVerification() throws Exception {
        var historyDir = tempDir.resolve(".history");
        var history = new SkillChangeHistory(historyDir);

        history.recordChange("my-skill", "edit", "dev-agent", "old content");

        Path jsonlFile = historyDir.resolve("my-skill.jsonl");
        assertThat(jsonlFile).exists();

        List<String> lines =
                Files.readAllLines(jsonlFile, StandardCharsets.UTF_8).stream()
                        .filter(l -> !l.isBlank())
                        .toList();
        assertThat(lines).hasSize(1);

        ObjectMapper mapper = new ObjectMapper();
        JsonNode node = mapper.readTree(lines.get(0));
        assertThat(node.has("timestamp")).isTrue();
        assertThat(node.has("operation")).isTrue();
        assertThat(node.has("agentName")).isTrue();
        assertThat(node.has("content")).isTrue();
        assertThat(node.get("operation").asText()).isEqualTo("edit");
        assertThat(node.get("agentName").asText()).isEqualTo("dev-agent");
        assertThat(node.get("content").asText()).isEqualTo("old content");
    }

    @Test
    void pruningKeepsOnlyMaxEntries() {
        var history = new SkillChangeHistory(tempDir.resolve(".history"), 3);

        for (int i = 1; i <= 5; i++) {
            history.recordChange("prune-test", "edit", "agent", "content-" + i);
        }

        List<HistoryEntry> entries = history.getHistory("prune-test");
        assertThat(entries).hasSize(3);
        // Should keep the last 3 (content-3, content-4, content-5)
        assertThat(entries.get(0).content()).isEqualTo("content-3");
        assertThat(entries.get(1).content()).isEqualTo("content-4");
        assertThat(entries.get(2).content()).isEqualTo("content-5");
    }

    @Test
    void perSkillLockIsolation() throws Exception {
        var history = new SkillChangeHistory(tempDir.resolve(".history"));
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        // Write to different skills concurrently
        for (int i = 0; i < threadCount; i++) {
            String skillName = "skill-" + i;
            executor.submit(
                    () -> {
                        try {
                            history.recordChange(
                                    skillName, "create", "agent-" + skillName, "content");
                        } finally {
                            latch.countDown();
                        }
                    });
        }

        assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
        executor.shutdown();

        // Each skill should have exactly one entry
        for (int i = 0; i < threadCount; i++) {
            List<HistoryEntry> entries = history.getHistory("skill-" + i);
            assertThat(entries).hasSize(1);
        }
    }

    @Test
    void agentNameCorrectlyRecordedAndRetrieved() {
        var history = new SkillChangeHistory(tempDir.resolve(".history"));

        history.recordChange("test-skill", "create", "alpha-agent", "c1");
        history.recordChange("test-skill", "edit", "beta-agent", "c2");

        List<HistoryEntry> entries = history.getHistory("test-skill");
        assertThat(entries.get(0).agentName()).isEqualTo("alpha-agent");
        assertThat(entries.get(1).agentName()).isEqualTo("beta-agent");
    }

    @Test
    void emptyHistoryReturnsEmptyList() {
        var history = new SkillChangeHistory(tempDir.resolve(".history"));

        List<HistoryEntry> entries = history.getHistory("nonexistent-skill");
        assertThat(entries).isEmpty();
    }

    @Test
    void historyDirectoryCreatedAutomatically() {
        Path historyDir = tempDir.resolve("nested").resolve("deep").resolve(".history");
        assertThat(historyDir).doesNotExist();

        var history = new SkillChangeHistory(historyDir);
        history.recordChange("auto-dir", "create", "agent", "content");

        assertThat(historyDir).exists().isDirectory();
        assertThat(history.getHistory("auto-dir")).hasSize(1);
    }
}
