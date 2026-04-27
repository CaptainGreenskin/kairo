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

import io.kairo.api.tool.Tool;
import io.kairo.api.tool.ToolCategory;
import io.kairo.api.tool.ToolContext;
import io.kairo.api.tool.ToolHandler;
import io.kairo.api.tool.ToolParam;
import io.kairo.api.tool.ToolResult;
import io.kairo.api.tool.ToolSideEffect;
import io.kairo.api.workspace.Workspace;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Writes multiple files in a single tool call.
 *
 * <p>Each file entry must carry a {@code path} and {@code content}. Per-file errors are non-fatal;
 * the remaining entries are still written. Returns aggregate success / error counts.
 */
@Tool(
        name = "batch_write",
        description =
                "Write multiple files in one call. Each entry needs 'path' and 'content'. "
                        + "Per-file errors are non-fatal. Maximum 20 files per call.",
        category = ToolCategory.FILE_AND_CODE,
        sideEffect = ToolSideEffect.WRITE)
public class BatchWriteTool implements ToolHandler {

    private static final int MAX_FILES = 20;

    @ToolParam(
            description =
                    "List of file entries. Each entry: {\"path\": \"...\", \"content\": \"...\"}",
            required = true)
    private List<Map<String, String>> files;

    @ToolParam(
            description =
                    "Automatically create parent directories if they do not exist (default true)")
    private Boolean createDirs;

    @Override
    public ToolResult execute(Map<String, Object> input) {
        return doExecute(input, Workspace.cwd().root());
    }

    @Override
    public ToolResult execute(Map<String, Object> input, ToolContext context) {
        return doExecute(input, context.workspace().root());
    }

    @SuppressWarnings("unchecked")
    private ToolResult doExecute(Map<String, Object> input, Path workspaceRoot) {
        Object rawFiles = input.get("files");
        if (!(rawFiles instanceof List<?> fileList) || fileList.isEmpty()) {
            return error("Parameter 'files' is required and must be a non-empty list");
        }
        if (fileList.size() > MAX_FILES) {
            return error("Too many files: maximum is " + MAX_FILES + ", got " + fileList.size());
        }

        boolean mkdirs = !(input.get("createDirs") instanceof Boolean b) || b; // default true

        List<Map<String, Object>> results = new ArrayList<>();
        int successCount = 0;
        int errorCount = 0;

        for (Object entry : fileList) {
            if (!(entry instanceof Map<?, ?> entryMap)) {
                results.add(fileError("<unknown>", "Entry is not a map"));
                errorCount++;
                continue;
            }
            Map<String, Object> m = (Map<String, Object>) entryMap;
            String path = m.get("path") instanceof String s ? s : null;
            String content = m.get("content") instanceof String s ? s : null;

            if (path == null || path.isBlank()) {
                results.add(fileError("<blank>", "Missing or blank 'path'"));
                errorCount++;
                continue;
            }

            Map<String, Object> fileResult = writeOne(workspaceRoot, path, content, mkdirs);
            results.add(fileResult);
            if (Boolean.TRUE.equals(fileResult.get("success"))) {
                successCount++;
            } else {
                errorCount++;
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Wrote ").append(successCount).append(" file(s)");
        if (errorCount > 0) sb.append(", ").append(errorCount).append(" error(s)");
        sb.append(":\n");
        for (Map<String, Object> r : results) {
            String status = Boolean.TRUE.equals(r.get("success")) ? "OK" : "ERROR";
            sb.append("  [").append(status).append("] ").append(r.get("path"));
            if (!Boolean.TRUE.equals(r.get("success"))) {
                sb.append(" — ").append(r.get("error"));
            }
            sb.append('\n');
        }

        return new ToolResult(
                "batch_write",
                sb.toString().stripTrailing(),
                false,
                Map.of("successCount", successCount, "errorCount", errorCount, "results", results));
    }

    private Map<String, Object> writeOne(
            Path workspaceRoot, String path, String content, boolean mkdirs) {
        try {
            Path resolved = workspaceRoot.resolve(path).normalize();
            if (!resolved.startsWith(workspaceRoot.normalize())) {
                return fileError(path, "Path escapes workspace root");
            }
            if (content == null) {
                return fileError(path, "Missing 'content'");
            }
            if (mkdirs) {
                Path parent = resolved.getParent();
                if (parent != null && !Files.exists(parent)) {
                    Files.createDirectories(parent);
                }
            }
            byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
            Files.write(resolved, bytes);
            return Map.of("path", path, "success", true, "bytesWritten", bytes.length);
        } catch (IOException e) {
            return fileError(path, e.getMessage());
        }
    }

    private static Map<String, Object> fileError(String path, String error) {
        return Map.of("path", path, "success", false, "error", error);
    }

    private ToolResult error(String msg) {
        return new ToolResult("batch_write", msg, true, Map.of());
    }
}
