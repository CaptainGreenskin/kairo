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
import io.kairo.api.tool.ToolSideEffect;
import io.kairo.core.memory.structured.MemoryDirectoryManager;
import io.kairo.core.memory.structured.MemoryFile;
import io.kairo.core.memory.structured.MemoryType;
import java.time.Instant;
import java.util.Map;
import reactor.core.publisher.Mono;

@Tool(
        name = "memory_write",
        description =
                "Write or update a structured memory file. Creates a frontmatter-based .md file"
                        + " in .kairo/memory/ and updates the MEMORY.md index.",
        category = ToolCategory.AGENT_AND_TASK,
        sideEffect = ToolSideEffect.WRITE)
public class MemoryWriteTool implements SyncTool {

    @ToolParam(
            description = "Kebab-case slug used as filename (e.g. 'feedback-testing')",
            required = true)
    private String name;

    @ToolParam(
            description = "One-line summary used in MEMORY.md index and relevance matching",
            required = true)
    private String description;

    @ToolParam(description = "Memory type: USER, FEEDBACK, PROJECT, or REFERENCE", required = true)
    private String type;

    @ToolParam(description = "Markdown body content of the memory", required = true)
    private String content;

    @Override
    public Mono<ToolResult> execute(Map<String, Object> args, ToolContext ctx) {
        return Mono.fromCallable(() -> doExecute(args, ctx));
    }

    private ToolResult doExecute(Map<String, Object> args, ToolContext ctx) {
        String nameVal = (String) args.get("name");
        String descVal = (String) args.get("description");
        String typeVal = (String) args.get("type");
        String contentVal = (String) args.get("content");

        if (nameVal == null || nameVal.isBlank()) {
            return ToolResult.error("memory_write", "Parameter 'name' is required");
        }
        if (descVal == null || descVal.isBlank()) {
            return ToolResult.error("memory_write", "Parameter 'description' is required");
        }
        if (typeVal == null || typeVal.isBlank()) {
            return ToolResult.error("memory_write", "Parameter 'type' is required");
        }
        if (contentVal == null || contentVal.isBlank()) {
            return ToolResult.error("memory_write", "Parameter 'content' is required");
        }

        MemoryType memoryType;
        try {
            memoryType = MemoryType.valueOf(typeVal.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ToolResult.error(
                    "memory_write",
                    "Invalid type '"
                            + typeVal
                            + "'. Must be one of: USER, FEEDBACK, PROJECT, REFERENCE");
        }

        try {
            MemoryDirectoryManager manager = createManager(ctx);
            MemoryFile existing = manager.read(nameVal);
            MemoryFile file =
                    new MemoryFile(nameVal, descVal, memoryType, contentVal, Instant.now());
            manager.write(file);

            String action = existing != null ? "Updated" : "Created";
            return ToolResult.success(
                    "memory_write",
                    action + " memory '" + nameVal + "' [" + memoryType + "]",
                    Map.of("name", nameVal, "type", memoryType.name(), "action", action));
        } catch (IllegalArgumentException e) {
            return ToolResult.error("memory_write", e.getMessage());
        } catch (RuntimeException e) {
            return ToolResult.error("memory_write", "Failed to write memory: " + e.getMessage());
        }
    }

    private static MemoryDirectoryManager createManager(ToolContext ctx) {
        return new MemoryDirectoryManager(ctx.workspace().root().resolve(".kairo/memory"));
    }
}
