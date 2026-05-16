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
package io.kairo.core.memory.structured;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;

public final class MemoryFileParser {

    private static final String DELIMITER = "---";
    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());

    private MemoryFileParser() {}

    public static MemoryFile parse(String markdown) {
        return parse(markdown, Instant.now());
    }

    public static MemoryFile parse(String markdown, Instant updatedAt) {
        if (markdown == null || markdown.isBlank()) {
            throw new IllegalArgumentException("Memory file content is empty");
        }

        String trimmed = markdown.strip();
        if (!trimmed.startsWith(DELIMITER)) {
            throw new IllegalArgumentException("Missing YAML front-matter delimiter '---'");
        }

        int secondDelimiter = trimmed.indexOf(DELIMITER, DELIMITER.length());
        if (secondDelimiter < 0) {
            throw new IllegalArgumentException("Missing closing YAML front-matter delimiter '---'");
        }

        String yamlBlock = trimmed.substring(DELIMITER.length(), secondDelimiter).strip();
        String body = trimmed.substring(secondDelimiter + DELIMITER.length()).strip();

        return parseFromYaml(yamlBlock, body, updatedAt);
    }

    @SuppressWarnings("unchecked")
    public static MemoryFile parseMetadataOnly(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            throw new IllegalArgumentException("Memory file content is empty");
        }

        String trimmed = markdown.strip();
        if (!trimmed.startsWith(DELIMITER)) {
            throw new IllegalArgumentException("Missing YAML front-matter delimiter '---'");
        }

        int secondDelimiter = trimmed.indexOf(DELIMITER, DELIMITER.length());
        if (secondDelimiter < 0) {
            throw new IllegalArgumentException("Missing closing YAML front-matter delimiter '---'");
        }

        String yamlBlock = trimmed.substring(DELIMITER.length(), secondDelimiter).strip();
        return parseFromYaml(yamlBlock, "", Instant.now());
    }

    public static String serialize(MemoryFile file) {
        StringBuilder sb = new StringBuilder();
        sb.append(DELIMITER).append('\n');
        sb.append("name: ").append(yamlSafe(file.name())).append('\n');
        sb.append("description: ").append(yamlSafe(file.description())).append('\n');
        sb.append("metadata:").append('\n');
        sb.append("  type: ").append(file.type().name().toLowerCase()).append('\n');
        sb.append(DELIMITER).append('\n');
        if (file.body() != null && !file.body().isBlank()) {
            sb.append('\n');
            sb.append(file.body()).append('\n');
        }
        return sb.toString();
    }

    private static String yamlSafe(String value) {
        if (value.indexOf(':') >= 0
                || value.indexOf('#') >= 0
                || value.indexOf('"') >= 0
                || value.indexOf('\'') >= 0
                || value.startsWith("{")
                || value.startsWith("[")
                || value.startsWith("- ")) {
            return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    private static MemoryFile parseFromYaml(String yamlBlock, String body, Instant updatedAt) {
        try {
            Map<String, Object> frontMatter = YAML_MAPPER.readValue(yamlBlock, Map.class);

            String name = requireString(frontMatter, "name");
            String description = requireString(frontMatter, "description");

            Object metadataObj = frontMatter.get("metadata");
            if (!(metadataObj instanceof Map<?, ?> metadata)) {
                throw new IllegalArgumentException("Missing required field: metadata.type");
            }

            Object typeObj = metadata.get("type");
            if (typeObj == null) {
                throw new IllegalArgumentException("Missing required field: metadata.type");
            }

            MemoryType type;
            try {
                type = MemoryType.valueOf(typeObj.toString().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(
                        "Invalid memory type: '"
                                + typeObj
                                + "'. Must be one of: USER, FEEDBACK, PROJECT, REFERENCE");
            }

            return new MemoryFile(name, description, type, body, updatedAt);
        } catch (IOException e) {
            throw new IllegalArgumentException("Invalid YAML front-matter: " + e.getMessage(), e);
        }
    }

    private static String requireString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            throw new IllegalArgumentException("Missing required field: " + key);
        }
        return value.toString();
    }
}
