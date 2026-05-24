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
 * Makes precise text replacements in a file.
 *
 * <p>The {@code originalText} must be unique within the file. If not found exactly, a trimmed
 * comparison is attempted as a fallback. Multiple matches cause an error to avoid ambiguous edits.
 *
 * <p>When an {@link LspService} is supplied, post-edit diagnostics introduced by this change are
 * attached to the result metadata under {@code "newDiagnostics"}.
 */
@Tool(
        name = "edit",
        description =
                "Make precise text replacements in a file. The original text must be unique in the file.",
        category = ToolCategory.FILE_AND_CODE,
        sideEffect = ToolSideEffect.WRITE)
public class EditTool implements SyncTool {

    private final FileAccessTracker fileTracker;
    private final PostEditDiagnosticsHook lspHook;

    /** Create an EditTool without file access tracking or LSP diagnostics. */
    public EditTool() {
        this(null, null);
    }

    public EditTool(FileAccessTracker fileTracker) {
        this(fileTracker, null);
    }

    /** Either argument may be null. */
    public EditTool(FileAccessTracker fileTracker, LspService lspService) {
        this.fileTracker = fileTracker;
        this.lspHook = lspService == null ? null : new PostEditDiagnosticsHook(lspService);
    }

    @ToolParam(description = "The absolute path of the file to edit", required = true)
    private String path;

    @ToolParam(description = "The exact text to find and replace", required = true)
    private String originalText;

    @ToolParam(description = "The replacement text", required = true)
    private String newText;

    @Override
    public Mono<ToolResult> execute(Map<String, Object> args, ToolContext ctx) {
        return Mono.fromCallable(() -> doExecute(args, ctx.workspace().root()));
    }

    private ToolResult doExecute(Map<String, Object> input, Path workspaceRoot) {
        String filePath = (String) input.get("path");
        String original = (String) input.get("originalText");
        String replacement = (String) input.get("newText");

        if (filePath == null || filePath.isBlank()) {
            return error("Parameter 'path' is required");
        }
        if (original == null) {
            return error("Parameter 'originalText' is required");
        }
        if (replacement == null) {
            return error("Parameter 'newText' is required");
        }

        Path file = workspaceRoot.resolve(filePath);
        if (!Files.exists(file)) {
            return error("File not found: " + filePath);
        }

        try {
            String content = Files.readString(file, StandardCharsets.UTF_8);

            // Exact match
            int count = countOccurrences(content, original);
            if (count == 1) {
                PostEditDiagnosticsHook.Token tok =
                        lspHook == null ? null : lspHook.beforeWrite(file);
                String updated = content.replace(original, replacement);
                Files.writeString(file, updated, StandardCharsets.UTF_8);
                if (fileTracker != null) {
                    fileTracker.recordAccess(filePath);
                }
                return successWithDiagnostics(
                        filePath, tok, updated, "Successfully edited " + filePath);
            }

            if (count > 1) {
                return error(
                        "Found "
                                + count
                                + " occurrences of the original text. "
                                + "Please provide more context to make the match unique.");
            }

            // Fallback: trimmed comparison (normalize whitespace per line)
            String trimmedOriginal = original.strip();
            String trimmedContent = content.strip();
            if (trimmedContent.contains(trimmedOriginal)) {
                int trimCount = countOccurrences(content, trimmedOriginal);
                if (trimCount == 1) {
                    PostEditDiagnosticsHook.Token tok =
                            lspHook == null ? null : lspHook.beforeWrite(file);
                    String updated = content.replace(trimmedOriginal, replacement);
                    Files.writeString(file, updated, StandardCharsets.UTF_8);
                    if (fileTracker != null) {
                        fileTracker.recordAccess(filePath);
                    }
                    return successWithDiagnostics(
                            filePath,
                            tok,
                            updated,
                            "Successfully edited "
                                    + filePath
                                    + " (matched after trimming whitespace)");
                }
            }

            return error(
                    "Could not find the specified text in "
                            + filePath
                            + ". Please verify the original text matches exactly.");

        } catch (IOException e) {
            return error("Failed to edit file: " + e.getMessage());
        }
    }

    private ToolResult successWithDiagnostics(
            String filePath, PostEditDiagnosticsHook.Token tok, String newContent, String message) {
        List<Diagnostic> introduced =
                lspHook == null ? List.of() : lspHook.afterWrite(tok, newContent);
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("path", filePath);
        if (!introduced.isEmpty()) {
            metadata.put("newDiagnostics", PostEditDiagnosticsHook.toMetadata(introduced));
        }
        return ToolResult.success("edit", message, metadata);
    }

    private int countOccurrences(String text, String search) {
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(search, idx)) != -1) {
            count++;
            idx += search.length();
        }
        return count;
    }

    private ToolResult error(String msg) {
        return ToolResult.error("edit", msg);
    }
}
