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
package io.kairo.multiagent.subagent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kairo.api.team.RoleDefinition;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Loads custom {@link ExpertProfile} definitions from JSON files in a directory (typically {@code
 * .kairo/roles/}).
 *
 * <p>Each {@code *.json} file defines one role:
 *
 * <pre>{@code
 * {
 *   "roleId": "custom:devops-engineer",
 *   "roleName": "DevOps Engineer",
 *   "instructions": "You are a DevOps engineer...",
 *   "skillProfile": "CI/CD, Docker, Kubernetes, monitoring",
 *   "capabilities": {
 *     "languages": ["shell", "yaml"],
 *     "frameworks": ["docker", "kubernetes", "terraform"],
 *     "domains": ["devops", "observability"],
 *     "actions": ["implement", "debug"]
 *   },
 *   "mountedSkills": [],
 *   "modelOverride": null,
 *   "allowedTools": []
 * }
 * }</pre>
 */
public final class FileRoleLoader {

    private static final Logger log = LoggerFactory.getLogger(FileRoleLoader.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private FileRoleLoader() {}

    /**
     * Load all role definitions from JSON files in the given directory.
     *
     * @param rolesDir the directory to scan (e.g., workspace/.kairo/roles/)
     * @return list of loaded profiles; malformed files are skipped with warnings
     */
    public static List<ExpertProfile> loadFromDirectory(Path rolesDir) {
        if (!Files.isDirectory(rolesDir)) {
            return List.of();
        }

        List<ExpertProfile> profiles = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(rolesDir, "*.json")) {
            for (Path file : stream) {
                try {
                    ExpertProfile profile = loadFromFile(file);
                    if (profile != null) {
                        profiles.add(profile);
                        log.debug("Loaded custom role '{}' from {}", profile.roleId(), file);
                    }
                } catch (Exception e) {
                    log.warn("Skipping malformed role file {}: {}", file, e.getMessage());
                }
            }
        } catch (IOException e) {
            log.warn("Failed to scan roles directory {}: {}", rolesDir, e.getMessage());
        }

        return List.copyOf(profiles);
    }

    /**
     * Load and register all roles from a directory into the given registry.
     *
     * @param rolesDir the directory to scan
     * @param registry the registry to populate
     * @return number of roles loaded
     */
    public static int loadIntoRegistry(Path rolesDir, ExpertRoleRegistry registry) {
        List<ExpertProfile> profiles = loadFromDirectory(rolesDir);
        for (ExpertProfile profile : profiles) {
            registry.register(profile.roleId(), profile);
        }
        return profiles.size();
    }

    static ExpertProfile loadFromFile(Path file) throws IOException {
        String content = Files.readString(file, StandardCharsets.UTF_8);
        JsonNode root = MAPPER.readTree(content);

        String roleId = requireString(root, "roleId");
        String roleName = optString(root, "roleName", roleId);
        String instructions = requireString(root, "instructions");
        String skillProfile = optString(root, "skillProfile", "");
        String modelOverride = optString(root, "modelOverride", null);

        List<String> allowedTools = parseStringList(root, "allowedTools");
        List<String> mountedSkills = parseStringList(root, "mountedSkills");
        RoleCapabilities capabilities = parseCapabilities(root.get("capabilities"));

        RoleDefinition roleDef =
                new RoleDefinition(roleId, roleName, instructions, "agent.default", allowedTools);

        return new ExpertProfile(
                roleId, roleDef, skillProfile, mountedSkills, roleId, modelOverride, capabilities);
    }

    private static RoleCapabilities parseCapabilities(JsonNode node) {
        if (node == null || !node.isObject()) {
            return RoleCapabilities.EMPTY;
        }
        Set<String> languages = parseStringSet(node.get("languages"));
        Set<String> frameworks = parseStringSet(node.get("frameworks"));
        Set<String> domains = parseStringSet(node.get("domains"));
        Set<String> actions = parseStringSet(node.get("actions"));
        return new RoleCapabilities(languages, frameworks, domains, actions);
    }

    private static Set<String> parseStringSet(JsonNode node) {
        if (node == null || !node.isArray()) {
            return Set.of();
        }
        Set<String> result = new LinkedHashSet<>();
        for (JsonNode item : node) {
            if (item.isTextual() && !item.asText().isBlank()) {
                result.add(item.asText());
            }
        }
        return result;
    }

    private static List<String> parseStringList(JsonNode root, String fieldName) {
        JsonNode node = root.get(fieldName);
        if (node == null || !node.isArray()) {
            return List.of();
        }
        try {
            return MAPPER.convertValue(node, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private static String requireString(JsonNode root, String fieldName) {
        JsonNode node = root.get(fieldName);
        if (node == null || !node.isTextual() || node.asText().isBlank()) {
            throw new IllegalArgumentException(
                    "Missing or blank required field: '" + fieldName + "'");
        }
        return node.asText();
    }

    private static String optString(JsonNode root, String fieldName, String defaultValue) {
        JsonNode node = root.get(fieldName);
        if (node == null || node.isNull() || !node.isTextual()) {
            return defaultValue;
        }
        return node.asText();
    }

    /** Maps the JSON field names for documentation. */
    @SuppressWarnings("unused")
    private static final Map<String, String> FIELD_DOCS =
            Map.of(
                    "roleId", "Unique role identifier (e.g., 'custom:devops-engineer')",
                    "roleName", "Human-readable role name",
                    "instructions", "System prompt instructions for this role",
                    "skillProfile", "Description of the role's skill set",
                    "capabilities", "Structured capabilities for task matching",
                    "mountedSkills", "Skill identifiers to mount",
                    "modelOverride", "Optional model ID for escalation",
                    "allowedTools", "Optional tool allowlist (empty = all)");
}
