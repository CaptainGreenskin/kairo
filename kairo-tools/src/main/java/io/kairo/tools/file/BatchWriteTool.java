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
 * <p>Each file entry contains {@code path}, {@code content}, and optional {@code createDirs}.
 * Supports {@code dryRun} mode to validate paths without writing. Uses two-phase execution: Phase 1
 * validates all paths, Phase 2 writes all files. If any write fails, previously written files are
 * rolled back. Returns per-file results with metadata {@code successCount} and {@code errorCount}.
 */
@Tool(
        name = "batch_write",
        description =
                "Write up to 50 files in a single call. Each file entry has 'path', 'content', and"
                        + " optional 'createDirs' (default true). Supports 'dryRun' to validate"
                        + " paths without writing. Uses two-phase execution: validates all paths"
                        + " first, then writes all. On failure, rolls back previously written files.",
        category = ToolCategory.FILE_AND_CODE,
        sideEffect = ToolSideEffect.WRITE)
public class BatchWriteTool implements ToolHandler {

    private static final int MAX_FILES = 50;

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

        boolean dryRun = Boolean.parseBoolean(input.getOrDefault("dryRun", "false").toString());

        Path normalRoot = workspaceRoot.toAbsolutePath().normalize();

        // Phase 1: Validate all entries and resolve paths
        // Store validation results in order
        List<ValidationResult> validations = new ArrayList<>();

        for (int i = 0; i < filesList.size(); i++) {
            Object item = filesList.get(i);
            if (!(item instanceof Map<?, ?> rawMap)) {
                validations.add(
                        new ValidationResult(
                                null, false, "Entry at index " + i + " must be an object"));
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
                validations.add(new ValidationResult(pathStr, false, "empty path"));
                continue;
            }

            if (content == null) {
                validations.add(
                        new ValidationResult(pathStr, false, "missing required field 'content'"));
                continue;
            }

            Path resolved = workspaceRoot.resolve(pathStr).toAbsolutePath().normalize();
            if (!resolved.startsWith(normalRoot)) {
                validations.add(
                        new ValidationResult(pathStr, false, "path traversal: outside workspace"));
                continue;
            }

            validations.add(
                    new ValidationResult(
                            pathStr,
                            true,
                            null,
                            new FileEntry(pathStr, content, createDirs, resolved)));
        }

        // Check if any validation errors
        boolean hasValidationErrors = validations.stream().anyMatch(v -> !v.success());
        int errorCount = (int) validations.stream().filter(v -> !v.success()).count();

        if (hasValidationErrors) {
            // Build results in order - mark all as failed
            List<Map<String, Object>> results = new ArrayList<>();
            for (ValidationResult v : validations) {
                if (v.success()) {
                    results.add(fileResult(v.path(), false, "skipped due to validation errors"));
                } else {
                    results.add(fileResult(v.path(), false, v.error()));
                }
            }
            return new ToolResult(
                    "batch_write",
                    buildContent(results),
                    false,
                    Map.of(
                            "successCount",
                            0,
                            "errorCount",
                            results.size(),
                            "dryRun",
                            dryRun,
                            "files",
                            results));
        }

        // All validations passed
        List<FileEntry> entries = validations.stream().map(ValidationResult::entry).toList();

        // Dry run: all paths valid, no actual writes
        if (dryRun) {
            List<Map<String, Object>> results = new ArrayList<>();
            for (FileEntry entry : entries) {
                results.add(fileResult(entry.path(), true, null));
            }
            return new ToolResult(
                    "batch_write",
                    "All paths validated successfully (dry run)",
                    false,
                    Map.of(
                            "successCount",
                            entries.size(),
                            "errorCount",
                            0,
                            "dryRun",
                            true,
                            "files",
                            results));
        }

        // Phase 2: Write all files with rollback on failure
        List<Path> writtenFiles = new ArrayList<>();
        boolean hasFailure = false;
        List<Map<String, Object>> results = new ArrayList<>();

        for (FileEntry entry : entries) {
            try {
                if (entry.createDirs()) {
                    Path parent = entry.resolvedPath().getParent();
                    if (parent != null && !Files.exists(parent)) {
                        Files.createDirectories(parent);
                    }
                }

                byte[] bytes = entry.content().getBytes(StandardCharsets.UTF_8);
                Files.write(entry.resolvedPath(), bytes);
                writtenFiles.add(entry.resolvedPath());

                results.add(fileResult(entry.path(), true, null));

            } catch (IOException e) {
                hasFailure = true;
                results.add(fileResult(entry.path(), false, e.getMessage()));
                break;
            }
        }

        // Rollback on failure
        if (hasFailure) {
            for (Path writtenPath : writtenFiles) {
                try {
                    Files.deleteIfExists(writtenPath);
                } catch (IOException ignored) {
                    // Best effort rollback
                }
            }

            // Update results to mark rolled-back files
            List<Map<String, Object>> rolledResults = new ArrayList<>();
            for (Map<String, Object> r : results) {
                if ((boolean) r.get("success")) {
                    Map<String, Object> rolled = new HashMap<>(r);
                    rolled.put("success", false);
                    rolled.put("error", "rolled back due to subsequent failure");
                    rolledResults.add(rolled);
                } else {
                    rolledResults.add(r);
                }
            }

            return new ToolResult(
                    "batch_write",
                    buildContent(rolledResults),
                    false,
                    Map.of(
                            "successCount",
                            0,
                            "errorCount",
                            rolledResults.size(),
                            "rolledBack",
                            true,
                            "files",
                            rolledResults));
        }

        int successCount = entries.size();
        return new ToolResult(
                "batch_write",
                buildContent(results),
                false,
                Map.of(
                        "successCount",
                        successCount,
                        "errorCount",
                        0,
                        "dryRun",
                        false,
                        "files",
                        results));
    }

    private record FileEntry(String path, String content, boolean createDirs, Path resolvedPath) {}

    private record ValidationResult(String path, boolean success, String error, FileEntry entry) {
        ValidationResult(String path, boolean success, String error) {
            this(path, success, error, null);
        }
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
