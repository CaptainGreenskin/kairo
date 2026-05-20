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
package io.kairo.tools.code;

import io.kairo.api.tool.SyncTool;
import io.kairo.api.tool.Tool;
import io.kairo.api.tool.ToolCategory;
import io.kairo.api.tool.ToolContext;
import io.kairo.api.tool.ToolParam;
import io.kairo.api.tool.ToolResult;
import io.kairo.core.lsp.LspClient;
import io.kairo.core.lsp.LspLocation;
import io.kairo.core.lsp.LspServerConfig;
import io.kairo.core.lsp.LspServerManager;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import reactor.core.publisher.Mono;

/**
 * Agent-facing tool for Language Server Protocol operations.
 *
 * <p>Supports goto definition, find references, and hover info. Auto-starts the appropriate
 * language server based on file extension. Returns formatted results suitable for LLM consumption.
 */
@Tool(
        name = "lsp",
        description =
                "Query language servers for code intelligence: goto definition, find references,"
                        + " hover info. Requires a running language server for the target language.",
        category = ToolCategory.FILE_AND_CODE)
public class LspTool implements SyncTool {

    private static final Map<Path, LspServerManager> MANAGERS = new ConcurrentHashMap<>();

    @ToolParam(description = "Operation: 'definition', 'references', or 'hover'", required = true)
    private String operation;

    @ToolParam(description = "Absolute path to the source file", required = true)
    private String filePath;

    @ToolParam(description = "Line number (1-based)", required = true)
    private int line;

    @ToolParam(description = "Column number (1-based)")
    private int column;

    private final LspServerManager overrideManager;

    public LspTool() {
        this.overrideManager = null;
    }

    LspTool(LspServerManager manager) {
        this.overrideManager = manager;
    }

    @Override
    public Mono<ToolResult> execute(Map<String, Object> args, ToolContext ctx) {
        String toolUseId = (String) args.getOrDefault("toolUseId", "");
        operation = (String) args.get("operation");
        filePath = (String) args.get("filePath");
        line = toInt(args.get("line"));
        column = toInt(args.getOrDefault("column", 1));

        if (operation == null || operation.isBlank()) {
            return Mono.just(
                    ToolResult.error(
                            toolUseId, "operation is required (definition/references/hover)"));
        }
        if (filePath == null || filePath.isBlank()) {
            return Mono.just(ToolResult.error(toolUseId, "filePath is required"));
        }

        Path path = Path.of(filePath);
        String ext = getExtension(path);
        if (ext.isEmpty()) {
            return Mono.just(
                    ToolResult.error(
                            toolUseId, "Cannot determine file extension for: " + filePath));
        }

        Path workspaceRoot = ctx.workspace().root();
        LspServerManager manager = getManager(workspaceRoot);
        LspClient client = manager.getClient(ext, workspaceRoot);
        if (client == null) {
            return Mono.just(
                    ToolResult.error(
                            toolUseId,
                            "No language server available for extension '"
                                    + ext
                                    + "'. Ensure the server binary is installed."));
        }

        return Mono.fromCallable(
                () -> {
                    String fileUri = path.toUri().toString();
                    int lspLine = line - 1;
                    int lspCol = column - 1;

                    return switch (operation) {
                        case "definition" -> {
                            List<LspLocation> locs =
                                    client.gotoDefinition(fileUri, lspLine, lspCol);
                            yield ToolResult.success(
                                    toolUseId, formatLocations("Definition", locs));
                        }
                        case "references" -> {
                            List<LspLocation> locs =
                                    client.findReferences(fileUri, lspLine, lspCol);
                            yield ToolResult.success(
                                    toolUseId, formatLocations("References", locs));
                        }
                        case "hover" -> {
                            String info = client.hover(fileUri, lspLine, lspCol);
                            if (info.isEmpty()) {
                                yield ToolResult.success(
                                        toolUseId,
                                        "No hover information at "
                                                + filePath
                                                + ":"
                                                + line
                                                + ":"
                                                + column);
                            }
                            yield ToolResult.success(toolUseId, info);
                        }
                        default ->
                                ToolResult.error(
                                        toolUseId,
                                        "Unknown operation '"
                                                + operation
                                                + "'. Use: definition, references, hover");
                    };
                });
    }

    private LspServerManager getManager(Path workspaceRoot) {
        if (overrideManager != null) {
            return overrideManager;
        }
        return MANAGERS.computeIfAbsent(
                workspaceRoot,
                root ->
                        new LspServerManager(
                                List.of(LspServerConfig.typescript(), LspServerConfig.python())));
    }

    private static String formatLocations(String header, List<LspLocation> locations) {
        if (locations.isEmpty()) {
            return "No " + header.toLowerCase() + " found.";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(header).append(" (").append(locations.size()).append(" result(s)):\n");
        for (LspLocation loc : locations) {
            sb.append("  ").append(loc.toHumanReadable()).append("\n");
        }
        return sb.toString().trim();
    }

    private static String getExtension(Path path) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot) : "";
    }

    private static int toInt(Object value) {
        if (value instanceof Number n) return n.intValue();
        if (value instanceof String s) return Integer.parseInt(s);
        return 1;
    }
}
