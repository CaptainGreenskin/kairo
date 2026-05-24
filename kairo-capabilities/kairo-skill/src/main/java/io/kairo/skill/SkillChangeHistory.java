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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Records JSONL change history for skills, supporting per-skill locks so that concurrent
 * modifications to different skills do not block each other.
 */
public class SkillChangeHistory {

    private static final Logger log = LoggerFactory.getLogger(SkillChangeHistory.class);

    private final Path historyDir;
    private final int maxEntries;
    private final ObjectMapper objectMapper;

    private final ConcurrentHashMap<String, Object> locks = new ConcurrentHashMap<>();

    public record HistoryEntry(
            @JsonProperty("timestamp") Instant timestamp,
            @JsonProperty("operation") String operation,
            @JsonProperty("agentName") String agentName,
            @JsonProperty("content") String content) {}

    public SkillChangeHistory(Path historyDir) {
        this(historyDir, 50);
    }

    public SkillChangeHistory(Path historyDir, int maxEntries) {
        this.historyDir = historyDir;
        this.maxEntries = maxEntries;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    private Object getLock(String skillName) {
        return locks.computeIfAbsent(skillName, k -> new Object());
    }

    public void recordChange(
            String skillName, String operation, String agentName, String oldContent) {
        synchronized (getLock(skillName)) {
            try {
                Files.createDirectories(historyDir);
                Path historyFile = historyDir.resolve(skillName + ".jsonl");

                List<String> lines = new ArrayList<>();
                if (Files.exists(historyFile)) {
                    lines.addAll(Files.readAllLines(historyFile, StandardCharsets.UTF_8));
                }

                HistoryEntry entry =
                        new HistoryEntry(Instant.now(), operation, agentName, oldContent);
                lines.add(objectMapper.writeValueAsString(entry));

                if (lines.size() > maxEntries) {
                    lines = new ArrayList<>(lines.subList(lines.size() - maxEntries, lines.size()));
                }

                Files.writeString(
                        historyFile, String.join("\n", lines) + "\n", StandardCharsets.UTF_8);
            } catch (IOException e) {
                log.error(
                        "Failed to record change history for skill '{}': {}",
                        skillName,
                        e.getMessage(),
                        e);
            }
        }
    }

    public List<HistoryEntry> getHistory(String skillName) {
        Path historyFile = historyDir.resolve(skillName + ".jsonl");
        if (!Files.exists(historyFile)) {
            return Collections.emptyList();
        }
        try {
            List<String> lines = Files.readAllLines(historyFile, StandardCharsets.UTF_8);
            List<HistoryEntry> entries = new ArrayList<>();
            for (String line : lines) {
                if (line.isBlank()) {
                    continue;
                }
                entries.add(objectMapper.readValue(line, HistoryEntry.class));
            }
            return entries;
        } catch (IOException e) {
            log.error(
                    "Failed to read change history for skill '{}': {}",
                    skillName,
                    e.getMessage(),
                    e);
            return Collections.emptyList();
        }
    }
}
