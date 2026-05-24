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
import java.util.Map;
import reactor.core.publisher.Mono;

@Tool(
        name = "memory_delete",
        description = "Delete a structured memory file and update the MEMORY.md index.",
        category = ToolCategory.AGENT_AND_TASK,
        sideEffect = ToolSideEffect.WRITE)
public class MemoryDeleteTool implements SyncTool {

    @ToolParam(description = "Name of the memory to delete", required = true)
    private String name;

    @Override
    public Mono<ToolResult> execute(Map<String, Object> args, ToolContext ctx) {
        return Mono.fromCallable(() -> doExecute(args, ctx));
    }

    private ToolResult doExecute(Map<String, Object> args, ToolContext ctx) {
        String nameVal = (String) args.get("name");
        if (nameVal == null || nameVal.isBlank()) {
            return ToolResult.error("memory_delete", "Parameter 'name' is required");
        }

        try {
            MemoryDirectoryManager manager = createManager(ctx);
            boolean deleted = manager.delete(nameVal);
            if (deleted) {
                return ToolResult.success("memory_delete", "Deleted memory '" + nameVal + "'");
            } else {
                return ToolResult.error(
                        "memory_delete", "No memory found with name '" + nameVal + "'");
            }
        } catch (IllegalArgumentException e) {
            return ToolResult.error("memory_delete", e.getMessage());
        } catch (RuntimeException e) {
            return ToolResult.error("memory_delete", "Failed to delete memory: " + e.getMessage());
        }
    }

    private static MemoryDirectoryManager createManager(ToolContext ctx) {
        return new MemoryDirectoryManager(ctx.workspace().root().resolve(".kairo/memory"));
    }
}
