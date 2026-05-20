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
package io.kairo.core.cron;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.kairo.api.cron.CronTask;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads and writes durable {@link CronTask}s to a JSON file.
 *
 * <p>File format: {@code {"tasks": [ ... ]}}
 */
public class CronTaskStore {

    private static final Logger log = LoggerFactory.getLogger(CronTaskStore.class);

    private static final TypeReference<Map<String, List<CronTask>>> FILE_TYPE =
            new TypeReference<>() {};

    private final Path filePath;
    private final ObjectMapper mapper;

    public CronTaskStore(Path filePath) {
        this.filePath = filePath;
        this.mapper = new ObjectMapper();
        this.mapper.registerModule(new JavaTimeModule());
        this.mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    CronTaskStore(Path filePath, ObjectMapper mapper) {
        this.filePath = filePath;
        this.mapper = mapper;
    }

    public List<CronTask> load() {
        if (!Files.exists(filePath)) {
            return new ArrayList<>();
        }
        try {
            String json = Files.readString(filePath, StandardCharsets.UTF_8);
            if (json.isBlank()) {
                return new ArrayList<>();
            }
            Map<String, List<CronTask>> wrapper = mapper.readValue(json, FILE_TYPE);
            List<CronTask> tasks = wrapper.get("tasks");
            return tasks != null ? new ArrayList<>(tasks) : new ArrayList<>();
        } catch (IOException e) {
            log.warn("Failed to load cron tasks from {}: {}", filePath, e.getMessage());
            return new ArrayList<>();
        }
    }

    public void save(List<CronTask> tasks) {
        try {
            Files.createDirectories(filePath.getParent());
            Map<String, List<CronTask>> wrapper = Map.of("tasks", tasks);
            String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(wrapper);
            Files.writeString(filePath, json, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("Failed to save cron tasks to {}: {}", filePath, e.getMessage());
        }
    }

    public Path filePath() {
        return filePath;
    }
}
