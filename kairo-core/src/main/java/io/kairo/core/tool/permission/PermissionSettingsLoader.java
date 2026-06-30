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
package io.kairo.core.tool.permission;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kairo.api.tool.ToolPermission;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Loads permission settings from a 3-layer JSON config hierarchy.
 *
 * <p>Config files (lowest to highest priority):
 *
 * <ol>
 *   <li>{@code ~/.kairo/permissions.json} — user-level defaults
 *   <li>{@code .kairo/permissions.json} — project-level (committed to git)
 *   <li>{@code .kairo/permissions.local.json} — local overrides (git-ignored)
 * </ol>
 *
 * <p>JSON format:
 *
 * <pre>{@code
 * {
 *   "mode": "strict",
 *   "allow": ["Read", "Bash(npm test*)"],
 *   "deny": ["Bash(rm -rf*)", "Write(/etc/*)"]
 * }
 * }</pre>
 */
public final class PermissionSettingsLoader {

    private static final Logger log = LoggerFactory.getLogger(PermissionSettingsLoader.class);

    private final ObjectMapper objectMapper;
    private final List<Path> configPaths;

    public PermissionSettingsLoader(ObjectMapper objectMapper, Path projectRoot) {
        this.objectMapper = objectMapper;
        Path userHome = Path.of(System.getProperty("user.home"));
        this.configPaths =
                List.of(
                        userHome.resolve(".kairo/permissions.json"),
                        projectRoot.resolve(".kairo/permissions.json"),
                        projectRoot.resolve(".kairo/permissions.local.json"));
    }

    public PermissionSettingsLoader(ObjectMapper objectMapper, List<Path> configPaths) {
        this.objectMapper = objectMapper;
        this.configPaths = List.copyOf(configPaths);
    }

    /**
     * Load and merge settings from all config layers.
     *
     * @return the merged settings
     */
    public PermissionSettings load() {
        PermissionSettings result = PermissionSettings.defaults();
        for (Path path : configPaths) {
            if (Files.exists(path) && Files.isReadable(path)) {
                try {
                    PermissionSettings layer = loadFrom(path);
                    result = result.merge(layer);
                    log.debug("Loaded permission settings from {}", path);
                } catch (Exception e) {
                    log.warn(
                            "Failed to load permission settings from {}: {}", path, e.getMessage());
                }
            }
        }
        return result;
    }

    PermissionSettings loadFrom(Path path) throws IOException {
        JsonNode root = objectMapper.readTree(path.toFile());
        if (!root.isObject()) {
            throw new IOException("Expected JSON object in " + path);
        }

        PermissionMode mode = parseMode(root);
        List<PermissionRule> rules = new ArrayList<>();

        parseRuleList(root, "deny", ToolPermission.DENIED, rules);
        parseRuleList(root, "allow", ToolPermission.ALLOWED, rules);

        return new PermissionSettings(mode, rules);
    }

    private PermissionMode parseMode(JsonNode root) {
        JsonNode modeNode = root.get("mode");
        if (modeNode == null || !modeNode.isTextual()) {
            return null;
        }
        String raw = modeNode.asText().strip();
        String modeText = raw.toUpperCase();
        // Accept intuitive aliases a user is likely to type, so a typo doesn't silently fall back
        // to DEFAULT (which would surprise them with approval prompts on every write).
        switch (modeText) {
            case "ALLOW", "AUTO", "AUTO_APPROVE", "YIELD", "AUTO_APPROVAL" -> {
                return PermissionMode.BYPASS;
            }
            case "ASK", "REQUIRE_APPROVAL" -> {
                return PermissionMode.STRICT;
            }
            case "READONLY", "READ_ONLY" -> {
                return PermissionMode.PLAN;
            }
            default -> {
                // fall through to enum lookup
            }
        }
        try {
            return PermissionMode.valueOf(modeText);
        } catch (IllegalArgumentException e) {
            log.warn(
                    "Unknown permission mode '{}'. Valid modes: DEFAULT, PLAN, STRICT, BYPASS "
                            + "(aliases: allow→BYPASS, strict/ask→STRICT, plan/readonly→PLAN). "
                            + "Falling back to DEFAULT.",
                    raw);
            return null;
        }
    }

    private void parseRuleList(
            JsonNode root,
            String fieldName,
            ToolPermission permission,
            List<PermissionRule> target) {
        JsonNode array = root.get(fieldName);
        if (array == null || !array.isArray()) {
            return;
        }
        for (JsonNode item : array) {
            if (!item.isTextual()) {
                log.warn("Skipping non-string entry in '{}' list: {}", fieldName, item);
                continue;
            }
            try {
                target.add(PermissionRule.parse(item.asText(), permission));
            } catch (IllegalArgumentException e) {
                log.warn(
                        "Skipping malformed rule '{}' in '{}' list: {}",
                        item.asText(),
                        fieldName,
                        e.getMessage());
            }
        }
    }
}
