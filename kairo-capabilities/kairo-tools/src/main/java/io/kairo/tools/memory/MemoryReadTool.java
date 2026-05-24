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
package io.kairo.tools.memory;

import io.kairo.api.tool.SyncTool;
import io.kairo.api.tool.Tool;
import io.kairo.api.tool.ToolCategory;
import io.kairo.api.tool.ToolContext;
import io.kairo.api.tool.ToolParam;
import io.kairo.api.tool.ToolResult;
import io.kairo.core.memory.structured.MemoryDirectoryManager;
import io.kairo.core.memory.structured.MemoryFile;
import java.util.List;
import java.util.Map;
import reactor.core.publisher.Mono;

@Tool(
        name = "memory_read",
        description =
                "Read structured memories. With no args returns the MEMORY.md index. "
                        + "With 'name' returns a specific memory. With 'query' searches for relevant memories.",
        category = ToolCategory.AGENT_AND_TASK)
public class MemoryReadTool implements SyncTool {

    @ToolParam(description = "Name of a specific memory to read")
    private String name;

    @ToolParam(description = "Search query to find relevant memories")
    private String query;

    @Override
    public Mono<ToolResult> execute(Map<String, Object> args, ToolContext ctx) {
        return Mono.fromCallable(() -> doExecute(args, ctx));
    }

    private ToolResult doExecute(Map<String, Object> args, ToolContext ctx) {
        String nameVal = (String) args.get("name");
        String queryVal = (String) args.get("query");

        MemoryDirectoryManager manager = createManager(ctx);

        if (nameVal != null && !nameVal.isBlank()) {
            return readByName(manager, nameVal);
        }
        if (queryVal != null && !queryVal.isBlank()) {
            return searchByQuery(manager, queryVal);
        }
        return returnIndex(manager);
    }

    private ToolResult readByName(MemoryDirectoryManager manager, String name) {
        try {
            MemoryFile file = manager.read(name);
            if (file == null) {
                return ToolResult.error("memory_read", "No memory found with name '" + name + "'");
            }

            StringBuilder sb = new StringBuilder();
            sb.append("Name: ").append(file.name()).append('\n');
            sb.append("Type: ").append(file.type()).append('\n');
            sb.append("Description: ").append(file.description()).append('\n');
            sb.append("Updated: ").append(file.updatedAt()).append('\n');
            sb.append('\n');
            sb.append(file.body());

            return ToolResult.success(
                    "memory_read",
                    sb.toString(),
                    Map.of("name", file.name(), "type", file.type().name()));
        } catch (IllegalArgumentException e) {
            return ToolResult.error("memory_read", e.getMessage());
        }
    }

    private ToolResult searchByQuery(MemoryDirectoryManager manager, String query) {
        List<MemoryFile> results = manager.search(query, 5);
        if (results.isEmpty()) {
            return ToolResult.success("memory_read", "No memories found matching '" + query + "'");
        }

        StringBuilder sb = new StringBuilder();
        sb.append(results.size()).append(" result(s) for '").append(query).append("':\n\n");
        for (MemoryFile file : results) {
            sb.append("  [").append(file.type()).append("] ").append(file.name());
            sb.append(" — ").append(file.description()).append('\n');
        }

        return ToolResult.success(
                "memory_read", sb.toString().stripTrailing(), Map.of("count", results.size()));
    }

    private ToolResult returnIndex(MemoryDirectoryManager manager) {
        String index = manager.loadIndex();
        if (index.isBlank()) {
            return ToolResult.success("memory_read", "No memories stored yet.");
        }
        return ToolResult.success("memory_read", index);
    }

    private static MemoryDirectoryManager createManager(ToolContext ctx) {
        return new MemoryDirectoryManager(ctx.workspace().root().resolve(".kairo/memory"));
    }
}
