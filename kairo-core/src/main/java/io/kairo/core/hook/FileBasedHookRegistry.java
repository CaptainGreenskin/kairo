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
package io.kairo.core.hook;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kairo.api.hook.ExternalHookBinding;
import io.kairo.api.hook.ExternalHookConfig;
import io.kairo.api.hook.HookPhase;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Loads external hook bindings from JSON settings files and registers them with a {@link
 * DefaultHookChain}.
 *
 * <p>Supports a 3-layer config hierarchy (merged in order, later layers override):
 *
 * <ol>
 *   <li><strong>User-level</strong> — {@code ~/.kairo/hooks.json}
 *   <li><strong>Project-level</strong> — {@code .kairo/hooks.json} (in project root)
 *   <li><strong>Local-level</strong> — {@code .kairo/hooks.local.json} (git-ignored)
 * </ol>
 *
 * <p>Config format:
 *
 * <pre>{@code
 * {
 *   "hooks": {
 *     "PRE_ACTING": [
 *       { "type": "command", "command": "my-script.sh", "timeout": 60, "matcher": "Bash|Read" }
 *     ],
 *     "POST_ACTING": [
 *       { "type": "http", "url": "http://...", "headers": {"Authorization": "$API_KEY"},
 *         "allowedEnvVars": ["API_KEY"] }
 *     ]
 *   }
 * }
 * }</pre>
 */
public class FileBasedHookRegistry {

    private static final Logger log = LoggerFactory.getLogger(FileBasedHookRegistry.class);

    private final ObjectMapper objectMapper;
    private final List<Path> configPaths;

    public FileBasedHookRegistry(ObjectMapper objectMapper, List<Path> configPaths) {
        this.objectMapper = objectMapper;
        this.configPaths = List.copyOf(configPaths);
    }

    public FileBasedHookRegistry(ObjectMapper objectMapper, Path projectRoot) {
        this.objectMapper = objectMapper;
        Path userHome = Path.of(System.getProperty("user.home"));
        this.configPaths =
                List.of(
                        userHome.resolve(".kairo/hooks.json"),
                        projectRoot.resolve(".kairo/hooks.json"),
                        projectRoot.resolve(".kairo/hooks.local.json"));
    }

    /**
     * Load all config files and register bindings with the given chain. Clears any existing
     * external bindings before loading.
     *
     * @param chain the hook chain to register bindings with
     * @return the number of bindings registered
     */
    public int loadAndRegister(DefaultHookChain chain) {
        chain.clearExternalBindings();
        List<ExternalHookBinding> bindings = loadAll();
        for (ExternalHookBinding binding : bindings) {
            chain.registerExternalBinding(binding);
        }
        log.info(
                "Loaded {} external hook bindings from {} config files",
                bindings.size(),
                configPaths.size());
        return bindings.size();
    }

    /**
     * Load bindings from all config files, merging in order.
     *
     * @return the merged list of bindings
     */
    public List<ExternalHookBinding> loadAll() {
        List<ExternalHookBinding> result = new ArrayList<>();
        for (Path path : configPaths) {
            if (Files.exists(path) && Files.isReadable(path)) {
                try {
                    List<ExternalHookBinding> bindings = loadFrom(path);
                    result.addAll(bindings);
                    log.debug("Loaded {} bindings from {}", bindings.size(), path);
                } catch (Exception e) {
                    log.warn("Failed to load hook config from {}: {}", path, e.getMessage());
                }
            }
        }
        return result;
    }

    List<ExternalHookBinding> loadFrom(Path path) throws IOException {
        List<ExternalHookBinding> result = new ArrayList<>();
        JsonNode root = objectMapper.readTree(path.toFile());

        JsonNode hooksNode = root.has("hooks") ? root.get("hooks") : root;
        if (!hooksNode.isObject()) return result;

        Iterator<Map.Entry<String, JsonNode>> fields = hooksNode.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            HookPhase phase = parsePhase(entry.getKey());
            if (phase == null) {
                log.warn("Unknown hook phase '{}' in {}", entry.getKey(), path);
                continue;
            }

            JsonNode array = entry.getValue();
            if (!array.isArray()) continue;

            for (JsonNode hookNode : array) {
                ExternalHookConfig config = parseConfig(hookNode);
                if (config != null) {
                    result.add(new ExternalHookBinding(phase, config));
                }
            }
        }
        return result;
    }

    private HookPhase parsePhase(String name) {
        try {
            return HookPhase.valueOf(name);
        } catch (IllegalArgumentException e) {
            // try case-insensitive
        }
        for (HookPhase phase : HookPhase.values()) {
            if (phase.name().equalsIgnoreCase(name)) return phase;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private ExternalHookConfig parseConfig(JsonNode node) {
        if (!node.isObject()) return null;

        String type = node.has("type") ? node.get("type").asText() : "command";
        String command = node.has("command") ? node.get("command").asText() : null;
        String url = node.has("url") ? node.get("url").asText() : null;

        Map<String, String> headers = Map.of();
        if (node.has("headers") && node.get("headers").isObject()) {
            headers = objectMapper.convertValue(node.get("headers"), Map.class);
        }

        List<String> allowedEnvVars = List.of();
        if (node.has("allowedEnvVars") && node.get("allowedEnvVars").isArray()) {
            allowedEnvVars = objectMapper.convertValue(node.get("allowedEnvVars"), List.class);
        }

        Duration timeout = null;
        if (node.has("timeout")) {
            timeout = Duration.ofSeconds(node.get("timeout").asLong());
        }

        String matcher = node.has("matcher") ? node.get("matcher").asText() : null;
        String ifCondition = node.has("if") ? node.get("if").asText() : null;

        return new ExternalHookConfig(
                type, command, url, headers, allowedEnvVars, timeout, matcher, ifCondition);
    }
}
