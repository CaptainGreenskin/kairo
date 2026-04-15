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
package io.kairo.core.model;

import io.kairo.api.model.ToolVerbosity;
import io.kairo.api.tool.ToolDefinition;
import java.util.List;

/**
 * Adapts tool descriptions based on model verbosity preferences.
 *
 * <p>For {@link ToolVerbosity#CONCISE} models (e.g. Haiku), tool descriptions are truncated to the
 * first sentence or 100 characters to save tokens. For {@link ToolVerbosity#VERBOSE} models (e.g.
 * Opus), usage hints are appended to help the model understand when to use each tool.
 *
 * <p>{@link ToolVerbosity#STANDARD} descriptions are returned unchanged.
 */
public class ToolDescriptionAdapter {

    /**
     * Adapt tool descriptions for the given verbosity level.
     *
     * @param tools the original tool definitions
     * @param verbosity the target verbosity
     * @return adapted tool definitions (or the original list if STANDARD)
     */
    public List<ToolDefinition> adaptForModel(List<ToolDefinition> tools, ToolVerbosity verbosity) {
        if (tools == null || verbosity == ToolVerbosity.STANDARD) {
            return tools;
        }

        return tools.stream().map(tool -> adaptTool(tool, verbosity)).toList();
    }

    private ToolDefinition adaptTool(ToolDefinition tool, ToolVerbosity verbosity) {
        return switch (verbosity) {
            case CONCISE -> conciseTool(tool);
            case VERBOSE -> verboseTool(tool);
            default -> tool;
        };
    }

    private ToolDefinition conciseTool(ToolDefinition tool) {
        String desc = tool.description();
        if (desc == null || desc.isEmpty()) {
            return tool;
        }
        int firstDot = desc.indexOf(". ");
        if (firstDot > 0 && firstDot < 100) {
            desc = desc.substring(0, firstDot + 1);
        } else if (desc.length() > 100) {
            desc = desc.substring(0, 100) + "...";
        }
        return new ToolDefinition(
                tool.name(),
                desc,
                tool.category(),
                tool.inputSchema(),
                tool.implementationClass(),
                tool.timeout(),
                tool.sideEffect());
    }

    private ToolDefinition verboseTool(ToolDefinition tool) {
        String desc = tool.description();
        if (desc == null) {
            return tool;
        }
        if (!desc.contains("Example") && !desc.contains("example")) {
            desc += " Use this tool when you need to " + inferUsageHint(tool.name()) + ".";
        }
        return new ToolDefinition(
                tool.name(),
                desc,
                tool.category(),
                tool.inputSchema(),
                tool.implementationClass(),
                tool.timeout(),
                tool.sideEffect());
    }

    private String inferUsageHint(String toolName) {
        if (toolName == null) {
            return "perform this operation";
        }
        String lower = toolName.toLowerCase();
        if (lower.contains("read")) return "read file contents";
        if (lower.contains("write")) return "create or overwrite files";
        if (lower.contains("edit")) return "make targeted edits to existing files";
        if (lower.contains("grep")) return "search for patterns in files";
        if (lower.contains("bash")) return "execute shell commands";
        if (lower.contains("glob")) return "find files by pattern";
        if (lower.contains("list")) return "list directory contents";
        return "perform this operation";
    }
}
