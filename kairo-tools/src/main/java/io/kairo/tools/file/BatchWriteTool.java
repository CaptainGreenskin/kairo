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
 * Atomically writes multiple files in a single call.
 *
 * <p>Two-phase execution: first validates all paths (workspace boundary, no traversal), then writes
 * all files. On any write failure, attempts to roll back already-written files.
 */
@Tool(
        name = "batch_write",
        description =
                "Write multiple files atomically in one call. Validates all paths first, then"
                        + " writes all. Rolls back on failure. Max 50 files per call.",
        category = ToolCategory.FILE_AND_CODE,
        sideEffect = ToolSideEffect.WRITE)
public class BatchWriteTool implements ToolHandler {

    static final int MAX_FILES = 50;

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
        if (filesRaw == null) return error("Parameter 'files' is required");
        if (!(filesRaw instanceof List)) return error("Parameter 'files' must be an array");

        List<Object> filesList = (List<Object>) filesRaw;
        if (filesList.isEmpty()) return error("Parameter 'files' must not be empty");
        if (filesList.size() > MAX_FILES) {
            return error("Too many files: max " + MAX_FILES + " per call, got " + filesList.size());
        }

        boolean dryRun = "true".equalsIgnoreCase(input.getOrDefault("dryRun", "false").toString());

        // Phase 1: validate all entries
        List<FileEntry> entries = new ArrayList<>(filesList.size());
        Path normalRoot = workspaceRoot.toAbsolutePath().normalize();

        for (int i = 0; i < filesList.size(); i++) {
            Object item = filesList.get(i);
            if (!(item instanceof Map)) {
                return error("File at index " + i + " must be an object");
            }
            Map<String, Object> fileMap = (Map<String, Object>) item;

            String pathStr = (String) fileMap.get("path");
            String content = (String) fileMap.get("content");
            boolean createDirs =
                    !"false"
                            .equalsIgnoreCase(
                                    fileMap.getOrDefault("createDirs", "true").toString());

            if (pathStr == null || pathStr.isBlank()) {
                return error("File at index " + i + " is missing required field 'path'");
            }
            if (content == null) {
                return error("File at index " + i + " is missing required field 'content'");
            }

            Path resolved = workspaceRoot.resolve(pathStr).toAbsolutePath().normalize();
            if (!resolved.startsWith(normalRoot)) {
                return error("Path traversal rejected: '" + pathStr + "' is outside workspace");
            }

            entries.add(new FileEntry(pathStr, resolved, content, createDirs));
        }

        if (dryRun) {
            List<Map<String, Object>> results = new ArrayList<>();
            for (FileEntry e : entries) {
                results.add(Map.of("path", e.pathStr(), "status", "valid"));
            }
            return new ToolResult(
                    "batch_write",
                    "Dry run: " + entries.size() + " paths validated",
                    false,
                    Map.of("dryRun", true, "filesValidated", entries.size(), "files", results));
        }

        // Phase 2: write all files; rollback on any failure
        List<Path> written = new ArrayList<>();
        List<Map<String, Object>> results = new ArrayList<>();
        try {
            for (FileEntry entry : entries) {
                if (entry.createDirs()) {
                    Path parent = entry.path().getParent();
                    if (parent != null && !Files.exists(parent)) {
                        Files.createDirectories(parent);
                    }
                }
                byte[] bytes = entry.content().getBytes(StandardCharsets.UTF_8);
                Files.write(entry.path(), bytes);
                written.add(entry.path());

                Map<String, Object> r = new HashMap<>();
                r.put("path", entry.pathStr());
                r.put("status", "written");
                r.put("bytes", bytes.length);
                results.add(r);
            }
        } catch (IOException e) {
            // Best-effort rollback
            int rolledBack = 0;
            for (Path p : written) {
                try {
                    Files.delete(p);
                    rolledBack++;
                } catch (IOException ignored) {
                }
            }
            return error(
                    "Write failed after "
                            + written.size()
                            + " files; rolled back "
                            + rolledBack
                            + ": "
                            + e.getMessage());
        }

        return new ToolResult(
                "batch_write",
                "Successfully wrote " + entries.size() + " files",
                false,
                Map.of("filesWritten", entries.size(), "files", results));
    }

    private ToolResult error(String msg) {
        return new ToolResult("batch_write", msg, true, Map.of());
    }

    private record FileEntry(String pathStr, Path path, String content, boolean createDirs) {}
}
