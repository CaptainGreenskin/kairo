/*
 * Copyright 2025-2026 the Kairo authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package io.kairo.plugin.component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kairo.api.plugin.PluginComponent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Parses {@code hooks/hooks.json} into {@link PluginComponent.HookComponent} entries.
 *
 * <p>Schema (compatible with the Claude Code hooks.json format):
 *
 * <pre>{@code
 * {
 *   "disableAllHooks": false,
 *   "hooks": {
 *     "<EventName>": [
 *       {
 *         "matcher": "Bash|Edit",
 *         "hooks": [
 *           { "type": "command",  "command": "/path/to/script.sh", "timeout": 600 },
 *           { "type": "http",     "url": "https://example.com/hook" },
 *           { "type": "mcp_tool", "server": "x", "tool": "scan" },
 *           { "type": "prompt",   "prompt": "Is this safe?" },
 *           { "type": "agent",    "prompt": "Verify $ARGUMENTS" }
 *         ]
 *       }
 *     ]
 *   }
 * }
 * }</pre>
 *
 * <p>{@code disableAllHooks=true} causes the loader to return an empty list (callers can re-load
 * after the user toggles the flag). Action-specific fields are passed through verbatim into the
 * {@link PluginComponent.HookComponent.HookAction#config()} map; this loader does not validate
 * type-specific shapes — that is {@code HookExecutor}'s responsibility.
 */
public final class HookComponentLoader {

    private final ObjectMapper mapper = new ObjectMapper();

    /** Loads from {@code <pluginRoot>/hooks/hooks.json} if present, else returns empty list. */
    public List<PluginComponent.HookComponent> load(Path pluginRoot) throws IOException {
        Path hooksFile = pluginRoot.resolve("hooks").resolve("hooks.json");
        if (!Files.isRegularFile(hooksFile)) return List.of();

        JsonNode root = mapper.readTree(Files.newBufferedReader(hooksFile));
        if (root == null || root.isMissingNode() || root.isNull()) return List.of();

        if (root.path("disableAllHooks").asBoolean(false)) return List.of();

        JsonNode hooksObj = root.get("hooks");
        if (hooksObj == null || !hooksObj.isObject()) return List.of();

        List<PluginComponent.HookComponent> out = new ArrayList<>();
        Iterator<Map.Entry<String, JsonNode>> events = hooksObj.fields();
        while (events.hasNext()) {
            Map.Entry<String, JsonNode> entry = events.next();
            String eventName = entry.getKey();
            JsonNode bindings = entry.getValue();
            if (bindings == null || !bindings.isArray()) continue;

            for (JsonNode binding : bindings) {
                String matcher = textOrNull(binding, "matcher");
                List<PluginComponent.HookComponent.HookAction> actions =
                        parseActions(binding.get("hooks"));
                if (actions.isEmpty()) continue;
                out.add(new PluginComponent.HookComponent(eventName, matcher, actions));
            }
        }
        return out;
    }

    private List<PluginComponent.HookComponent.HookAction> parseActions(JsonNode actionsNode) {
        if (actionsNode == null || !actionsNode.isArray()) return List.of();
        List<PluginComponent.HookComponent.HookAction> out = new ArrayList<>();
        for (JsonNode actionNode : actionsNode) {
            if (actionNode == null || !actionNode.isObject()) continue;
            String type = textOrNull(actionNode, "type");
            if (type == null || type.isBlank()) continue;

            Map<String, Object> config = new HashMap<>();
            Iterator<Map.Entry<String, JsonNode>> fields = actionNode.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                if ("type".equals(field.getKey())) continue;
                config.put(field.getKey(), nodeToValue(field.getValue()));
            }
            out.add(new PluginComponent.HookComponent.HookAction(type, config));
        }
        return out;
    }

    private Object nodeToValue(JsonNode node) {
        if (node == null || node.isNull()) return null;
        if (node.isTextual()) return node.asText();
        if (node.isInt()) return node.asInt();
        if (node.isLong()) return node.asLong();
        if (node.isDouble() || node.isFloat()) return node.asDouble();
        if (node.isBoolean()) return node.asBoolean();
        // Arrays / objects pass through as the parsed Jackson form for downstream consumption.
        return mapper.convertValue(node, Object.class);
    }

    private String textOrNull(JsonNode parent, String field) {
        if (parent == null) return null;
        JsonNode n = parent.get(field);
        return (n != null && n.isTextual()) ? n.asText() : null;
    }
}
