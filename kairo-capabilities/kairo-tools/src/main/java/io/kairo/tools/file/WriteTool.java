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
package io.kairo.tools.file;

import io.kairo.api.lsp.Diagnostic;
import io.kairo.api.lsp.LspService;
import io.kairo.api.tool.SyncTool;
import io.kairo.api.tool.Tool;
import io.kairo.api.tool.ToolCategory;
import io.kairo.api.tool.ToolContext;
import io.kairo.api.tool.ToolParam;
import io.kairo.api.tool.ToolResult;
import io.kairo.api.tool.ToolSideEffect;
import io.kairo.core.context.recovery.FileAccessTracker;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import reactor.core.publisher.Mono;

/**
 * Creates or overwrites a file with the given content.
 *
 * <p>Automatically creates parent directories if they do not exist. The file is written using UTF-8
 * encoding.
 *
 * <p>If an {@link LspService} is supplied and willing to run for the target path (see {@link
 * LspService#enabledFor}), the tool snapshots diagnostics before the write, notifies the LSP server
 * afterward, and includes any newly introduced diagnostics in the result metadata under the {@code
 * "newDiagnostics"} key.
 */
@Tool(
        name = "write",
        description =
                "Create or overwrite a file with the given content. Automatically creates parent directories.",
        category = ToolCategory.FILE_AND_CODE,
        sideEffect = ToolSideEffect.WRITE)
public class WriteTool implements SyncTool {

    private final FileAccessTracker fileTracker;
    private final PostEditDiagnosticsHook lspHook;

    /** Create a WriteTool without file access tracking or LSP diagnostics. */
    public WriteTool() {
        this(null, null);
    }

    /** Create a WriteTool with file access tracking, no LSP diagnostics. */
    public WriteTool(FileAccessTracker fileTracker) {
        this(fileTracker, null);
    }

    /**
     * Create a WriteTool with optional file access tracking and optional LSP diagnostics. Either
     * may be null.
     */
    public WriteTool(FileAccessTracker fileTracker, LspService lspService) {
        this.fileTracker = fileTracker;
        this.lspHook = lspService == null ? null : new PostEditDiagnosticsHook(lspService);
    }

    @ToolParam(description = "The absolute path of the file to write", required = true)
    private String path;

    @ToolParam(description = "The content to write to the file", required = true)
    private String content;

    @Override
    public Mono<ToolResult> execute(Map<String, Object> args, ToolContext ctx) {
        return Mono.fromCallable(() -> doExecute(args, ctx.workspace().root()));
    }

    private ToolResult doExecute(Map<String, Object> input, Path workspaceRoot) {
        String filePath = (String) input.get("path");
        String fileContent = (String) input.get("content");

        if (filePath == null || filePath.isBlank()) {
            return error("Parameter 'path' is required");
        }
        if (fileContent == null) {
            return error("Parameter 'content' is required");
        }

        Path file = workspaceRoot.resolve(filePath);
        try {
            // Create parent directories if needed
            Path parent = file.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }

            PostEditDiagnosticsHook.Token lspToken =
                    lspHook == null ? null : lspHook.beforeWrite(file);

            byte[] bytes = fileContent.getBytes(StandardCharsets.UTF_8);
            Files.write(file, bytes);

            if (fileTracker != null) {
                fileTracker.recordAccess(filePath);
            }

            List<Diagnostic> introduced =
                    lspHook == null ? List.of() : lspHook.afterWrite(lspToken, fileContent);

            Map<String, Object> metadata = new HashMap<>();
            metadata.put("path", filePath);
            metadata.put("bytesWritten", bytes.length);
            if (!introduced.isEmpty()) {
                metadata.put("newDiagnostics", PostEditDiagnosticsHook.toMetadata(introduced));
            }

            return ToolResult.success(
                    "write",
                    "Successfully wrote " + bytes.length + " bytes to " + filePath,
                    metadata);

        } catch (IOException e) {
            return error("Failed to write file: " + e.getMessage());
        }
    }

    private ToolResult error(String msg) {
        return ToolResult.error("write", msg);
    }
}
