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
import io.kairo.plugin.variable.PluginVariableResolver;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Loads MCP server contributions from a plugin.
 *
 * <p>Two equivalent sources are supported:
 *
 * <ul>
 *   <li>{@code <pluginRoot>/.mcp.json} containing {@code {"mcpServers": {...}}}
 *   <li>{@code mcpServers} object inside the plugin manifest (passed through {@code
 *       PluginManifest#mcpServers()})
 * </ul>
 *
 * <p>Each server entry has the standard MCP shape:
 *
 * <pre>{@code
 * {
 *   "<server-name>": {
 *     "command": "/path/to/server",
 *     "args": ["--stdio"],
 *     "env": {"FOO": "bar"}
 *   }
 * }
 * }</pre>
 *
 * <p>String fields (command/args/env values) pass through {@link PluginVariableResolver} when one
 * is provided, expanding {@code ${KAIRO_PLUGIN_ROOT}} and friends so plugins can reference paths
 * inside their own install directory.
 */
public final class McpComponentLoader {

    private final ObjectMapper json = new ObjectMapper();

    /**
     * Reads {@code .mcp.json} from {@code pluginRoot} (if present) and merges with {@code
     * inlineServers} (typically the manifest's {@code mcpServers}). Inline values take precedence
     * over file values when the same server name appears in both — mirrors Claude Code's resolution
     * order.
     */
    public List<PluginComponent.McpComponent> load(
            Path pluginRoot, Map<String, Object> inlineServers, PluginVariableResolver resolver)
            throws IOException {
        Map<String, Object> merged = new HashMap<>();
        Path mcpFile = pluginRoot.resolve(".mcp.json");
        if (Files.isRegularFile(mcpFile)) {
            JsonNode root = json.readTree(Files.newBufferedReader(mcpFile));
            JsonNode servers = root == null ? null : root.get("mcpServers");
            if (servers != null && servers.isObject()) {
                Iterator<Map.Entry<String, JsonNode>> it = servers.fields();
                while (it.hasNext()) {
                    Map.Entry<String, JsonNode> entry = it.next();
                    merged.put(entry.getKey(), entry.getValue());
                }
            }
        }
        if (inlineServers != null) {
            merged.putAll(inlineServers);
        }
        if (merged.isEmpty()) return List.of();

        List<PluginComponent.McpComponent> out = new ArrayList<>();
        for (Map.Entry<String, Object> entry : merged.entrySet()) {
            PluginComponent.McpComponent comp =
                    parseEntry(entry.getKey(), entry.getValue(), resolver);
            if (comp != null) out.add(comp);
        }
        return out;
    }

    private PluginComponent.McpComponent parseEntry(
            String serverName, Object spec, PluginVariableResolver resolver) {
        Map<?, ?> mapSpec;
        if (spec instanceof JsonNode node && node.isObject()) {
            mapSpec = json.convertValue(node, Map.class);
        } else if (spec instanceof Map<?, ?> m) {
            mapSpec = m;
        } else {
            return null;
        }

        String command = stringOrNull(mapSpec, "command");
        if (command == null || command.isBlank()) return null;
        command = resolve(command, resolver);

        List<String> args = new ArrayList<>();
        Object argsRaw = mapSpec.get("args");
        if (argsRaw instanceof List<?> list) {
            for (Object a : list) {
                args.add(resolve(String.valueOf(a), resolver));
            }
        }

        Map<String, String> env = new HashMap<>();
        Object envRaw = mapSpec.get("env");
        if (envRaw instanceof Map<?, ?> envMap) {
            for (Map.Entry<?, ?> e : envMap.entrySet()) {
                env.put(
                        String.valueOf(e.getKey()),
                        resolve(String.valueOf(e.getValue()), resolver));
            }
        }

        return new PluginComponent.McpComponent(serverName, command, args, env);
    }

    private String stringOrNull(Map<?, ?> map, String key) {
        Object v = map.get(key);
        return v instanceof String s ? s : null;
    }

    private String resolve(String value, PluginVariableResolver resolver) {
        if (value == null) return null;
        return resolver == null ? value : resolver.resolve(value);
    }
}
