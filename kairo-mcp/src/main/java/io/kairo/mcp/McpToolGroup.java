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
package io.kairo.mcp;

import io.kairo.api.tool.ToolDefinition;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks the tools registered from a single MCP server, enabling bulk operations
 * like unregistration of all tools from one server.
 */
public class McpToolGroup {

    private final String serverName;
    private final List<String> registeredToolNames = new ArrayList<>();
    private final Map<String, McpToolExecutor> executors = new ConcurrentHashMap<>();
    private final Map<String, ToolDefinition> toolDefinitions = new ConcurrentHashMap<>();

    public McpToolGroup(String serverName) {
        this.serverName = serverName;
    }

    /** Adds a tool to this group. */
    void addTool(ToolDefinition definition, McpToolExecutor executor) {
        String name = definition.name();
        registeredToolNames.add(name);
        executors.put(name, executor);
        toolDefinitions.put(name, definition);
    }

    /** Returns the server name for this group. */
    public String getServerName() {
        return serverName;
    }

    /** Returns an unmodifiable list of all registered Kairo tool names. */
    public List<String> getRegisteredToolNames() {
        return Collections.unmodifiableList(registeredToolNames);
    }

    /** Returns the executor for the given Kairo tool name. */
    public McpToolExecutor getExecutor(String kairoToolName) {
        return executors.get(kairoToolName);
    }

    /** Returns the tool definition for the given Kairo tool name. */
    public ToolDefinition getToolDefinition(String kairoToolName) {
        return toolDefinitions.get(kairoToolName);
    }

    /** Returns all tool definitions in this group. */
    public List<ToolDefinition> getAllToolDefinitions() {
        return new ArrayList<>(toolDefinitions.values());
    }

    /** Returns the number of tools in this group. */
    public int size() {
        return registeredToolNames.size();
    }
}
