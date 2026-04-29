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
import io.kairo.api.tool.ToolResult;
import io.kairo.api.tool.ToolSideEffect;
import io.kairo.api.workspace.Workspace;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Writes multiple files in a single tool call, reducing tool call iterations for code generation.
 *
 * <p>Each file entry contains {@code path} and {@code content}. Individual file failures do not
 * abort the batch. Returns per-file results with metadata {@code successCount} and {@code
 * errorCount}.
 */
@Tool(
        name = "batch_write",
        description =
                "Write up to 20 files in a single call. Each file entry has 'path' and 'content'."
                        + " Individual failures do not abort the batch. Returns per-file results"
                        + " with successCount and errorCount metadata.",
        category = ToolCategory.FILE_AND_CODE,
        sideEffect = ToolSideEffect.WRITE)
public class BatchWriteTool implements ToolHandler {

    private static final int MAX_FILES = 20;

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
        Object filesRaw = input.get("files");
        if (!(filesRaw instanceof List<?> filesList) || filesList.isEmpty()) {
            return error("Parameter 'files' must be a non-empty JSON array of file entries");
        }
        if (filesList.size() > MAX_FILES) {
            return error("Too many files: maximum is " + MAX_FILES + ", got " + filesList.size());
        }

        Path normalRoot = workspaceRoot.toAbsolutePath().normalize();

        List<Map<String, Object>> results = new ArrayList<>();
        int successCount = 0;
        int errorCount = 0;

        for (int i = 0; i < filesList.size(); i++) {
            Object item = filesList.get(i);
            if (!(item instanceof Map<?, ?> rawMap)) {
                results.add(fileResult(null, false, "Entry at index " + i + " must be an object"));
                errorCount++;
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> fileMap = (Map<String, Object>) rawMap;

            String pathStr =
                    fileMap.get("path") != null ? fileMap.get("path").toString().trim() : null;
            String content = (String) fileMap.get("content");
            boolean createDirs =
                    !"false"
                            .equalsIgnoreCase(
                                    fileMap.getOrDefault("createDirs", "true").toString());

            if (pathStr == null || pathStr.isEmpty()) {
                results.add(fileResult(pathStr, false, "empty path"));
                errorCount++;
                continue;
            }

            if (content == null) {
                results.add(fileResult(pathStr, false, "missing required field 'content'"));
                errorCount++;
                continue;
            }

            Path resolved = workspaceRoot.resolve(pathStr).toAbsolutePath().normalize();
            if (!resolved.startsWith(normalRoot)) {
                results.add(fileResult(pathStr, false, "path traversal: outside workspace"));
                errorCount++;
                continue;
            }

            try {
                if (createDirs) {
                    Path parent = resolved.getParent();
                    if (parent != null && !Files.exists(parent)) {
                        Files.createDirectories(parent);
                    }
                }

                byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
                Files.write(resolved, bytes);

                results.add(fileResult(pathStr, true, null));
                successCount++;

            } catch (IOException e) {
                results.add(fileResult(pathStr, false, e.getMessage()));
                errorCount++;
            }
        }

        return new ToolResult(
                "batch_write",
                buildContent(results),
                false,
                Map.of("successCount", successCount, "errorCount", errorCount, "files", results));
    }

    private Map<String, Object> fileResult(String path, boolean success, String error) {
        Map<String, Object> r = new HashMap<>();
        r.put("path", path);
        r.put("success", success);
        if (error != null) {
            r.put("error", error);
        }
        return r;
    }

    private String buildContent(List<Map<String, Object>> results) {
        StringBuilder sb = new StringBuilder();
        for (Map<String, Object> r : results) {
            String path = (String) r.get("path");
            boolean success = (boolean) r.get("success");
            if (success) {
                sb.append("=== ").append(path).append(" ===\n");
                sb.append("Successfully written\n\n");
            } else {
                sb.append("=== ").append(path != null ? path : "(no path)").append(" ===\n");
                sb.append("[ERROR: ").append(r.get("error")).append("]\n\n");
            }
        }
        return sb.toString().stripTrailing();
    }

    private ToolResult error(String msg) {
        return new ToolResult("batch_write", msg, true, Map.of());
    }
}
