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
import io.kairo.api.workspace.Workspace;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Finds files matching a glob pattern within a directory tree.
 *
 * <p>Common directories like {@code .git}, {@code node_modules}, and {@code target} are
 * automatically skipped. Results are limited to 2000 entries.
 */
@Tool(
        name = "glob",
        description = "Find files matching a glob pattern. Useful to discover project structure.",
        category = ToolCategory.FILE_AND_CODE)
public class GlobTool implements ToolHandler {

    private static final int MAX_RESULTS = 2000;
    private static final Set<String> SKIP_DIRS =
            Set.of(
                    ".git",
                    "node_modules",
                    "target",
                    "build",
                    ".idea",
                    "__pycache__",
                    ".gradle",
                    "dist");

    @ToolParam(description = "The glob pattern to match (e.g. '**/*.java')", required = true)
    private String pattern;

    @ToolParam(description = "The directory to search in", required = true)
    private String path;

    @Override
    public ToolResult execute(Map<String, Object> input) {
        return doExecute(input, Workspace.cwd().root());
    }

    @Override
    public ToolResult execute(Map<String, Object> input, ToolContext context) {
        return doExecute(input, context.workspace().root());
    }

    private ToolResult doExecute(Map<String, Object> input, Path workspaceRoot) {
        String globPattern = (String) input.get("pattern");
        String searchPath = (String) input.get("path");

        if (globPattern == null || globPattern.isBlank()) {
            return error("Parameter 'pattern' is required");
        }
        if (searchPath == null || searchPath.isBlank()) {
            return error("Parameter 'path' is required");
        }

        Path dir = workspaceRoot.resolve(searchPath);
        if (!Files.isDirectory(dir)) {
            return error("Not a directory: " + searchPath);
        }

        try {
            PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + globPattern);
            List<String> matches = new ArrayList<>();

            Files.walkFileTree(
                    dir,
                    new SimpleFileVisitor<>() {
                        @Override
                        public FileVisitResult preVisitDirectory(
                                Path d, BasicFileAttributes attrs) {
                            if (SKIP_DIRS.contains(d.getFileName().toString())) {
                                return FileVisitResult.SKIP_SUBTREE;
                            }
                            return matches.size() < MAX_RESULTS
                                    ? FileVisitResult.CONTINUE
                                    : FileVisitResult.TERMINATE;
                        }

                        @Override
                        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                            if (matches.size() >= MAX_RESULTS) {
                                return FileVisitResult.TERMINATE;
                            }
                            Path relative = dir.relativize(file);
                            if (matcher.matches(relative)) {
                                matches.add(file.toString());
                            }
                            return FileVisitResult.CONTINUE;
                        }

                        @Override
                        public FileVisitResult visitFileFailed(Path file, IOException exc) {
                            return FileVisitResult.CONTINUE;
                        }
                    });

            if (matches.isEmpty()) {
                return new ToolResult(
                        "glob",
                        "No files matched pattern: " + globPattern,
                        false,
                        Map.of("count", 0, "pattern", globPattern, "path", searchPath));
            }

            String result = matches.stream().collect(Collectors.joining("\n"));
            boolean truncated = matches.size() >= MAX_RESULTS;
            if (truncated) {
                result += "\n... (truncated at " + MAX_RESULTS + " results)";
            }

            return new ToolResult(
                    "glob",
                    result,
                    false,
                    Map.of(
                            "count",
                            matches.size(),
                            "pattern",
                            globPattern,
                            "path",
                            searchPath,
                            "truncated",
                            truncated));

        } catch (IOException e) {
            return error("Failed to search: " + e.getMessage());
        }
    }

    private ToolResult error(String msg) {
        return new ToolResult("glob", msg, true, Map.of());
    }
}
