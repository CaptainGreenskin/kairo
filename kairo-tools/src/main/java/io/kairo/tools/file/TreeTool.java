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
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Renders an ASCII directory tree, similar to the Unix {@code tree} command, using pure Java NIO.
 */
@Tool(
        name = "tree",
        description =
                "Show a directory as an ASCII tree (like Unix tree). Supports depth limit, file"
                        + " filtering, and pattern exclusion.",
        category = ToolCategory.FILE_AND_CODE,
        sideEffect = ToolSideEffect.READ_ONLY)
public class TreeTool implements ToolHandler {

    private static final int MAX_ENTRIES = 1000;

    @Override
    public ToolResult execute(Map<String, Object> input) {
        return doExecute(input, Workspace.cwd().root());
    }

    @Override
    public ToolResult execute(Map<String, Object> input, ToolContext context) {
        return doExecute(input, context.workspace().root());
    }

    private ToolResult doExecute(Map<String, Object> input, Path workspaceRoot) {
        String pathStr = input.getOrDefault("path", ".").toString();
        int maxDepth = parseIntOrDefault(input.get("maxDepth"), 3, 0);
        boolean includeFiles =
                !"false".equalsIgnoreCase(input.getOrDefault("includeFiles", "true").toString());

        String patternStr = input.containsKey("pattern") ? input.get("pattern").toString() : null;
        String excludeStr =
                input.containsKey("excludePatterns")
                        ? input.get("excludePatterns").toString()
                        : null;

        PathMatcher fileMatcher =
                patternStr != null
                        ? FileSystems.getDefault().getPathMatcher("glob:" + patternStr)
                        : null;
        List<PathMatcher> excludeMatchers = buildExcludeMatchers(excludeStr);

        Path target = resolvePath(pathStr, workspaceRoot);
        if (!Files.exists(target)) {
            return error("Path does not exist: " + pathStr);
        }
        if (!Files.isDirectory(target)) {
            return error("Path is not a directory: " + pathStr);
        }

        StringBuilder sb = new StringBuilder();
        String rootName =
                target.getFileName() != null ? target.getFileName().toString() : target.toString();
        sb.append(rootName).append("/\n");

        int[] counts = {0, 0}; // [files, dirs]
        boolean[] truncated = {false};

        try {
            renderTree(
                    target,
                    "",
                    maxDepth,
                    0,
                    includeFiles,
                    fileMatcher,
                    excludeMatchers,
                    sb,
                    counts,
                    truncated);
        } catch (IOException e) {
            return error("Failed to read directory: " + e.getMessage());
        }

        if (truncated[0]) {
            sb.append("... (truncated at ").append(MAX_ENTRIES).append(" entries)\n");
        }

        return new ToolResult(
                "tree",
                sb.toString(),
                false,
                Map.of("totalFiles", counts[0], "totalDirs", counts[1], "truncated", truncated[0]));
    }

    private void renderTree(
            Path dir,
            String prefix,
            int maxDepth,
            int depth,
            boolean includeFiles,
            PathMatcher fileMatcher,
            List<PathMatcher> excludeMatchers,
            StringBuilder sb,
            int[] counts,
            boolean[] truncated)
            throws IOException {

        if (truncated[0]) return;

        List<Path> children = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path child : stream) {
                if (isExcluded(child, excludeMatchers)) continue;
                boolean isDir = Files.isDirectory(child);
                if (!includeFiles && !isDir) continue;
                if (fileMatcher != null && !isDir && !fileMatcher.matches(child.getFileName()))
                    continue;
                children.add(child);
            }
        }

        children.sort(
                Comparator.comparing(
                        p -> (Files.isDirectory(p) ? "0" : "1") + p.getFileName().toString()));

        for (int i = 0; i < children.size(); i++) {
            if (truncated[0]) return;

            Path child = children.get(i);
            boolean isLast = i == children.size() - 1;
            boolean isDir = Files.isDirectory(child);
            String name = child.getFileName().toString();

            sb.append(prefix).append(isLast ? "└── " : "├── ").append(name);
            if (isDir) {
                sb.append("/");
                counts[1]++;
            } else {
                counts[0]++;
            }
            sb.append("\n");

            if (counts[0] + counts[1] >= MAX_ENTRIES) {
                truncated[0] = true;
                return;
            }

            if (isDir && depth < maxDepth) {
                String childPrefix = prefix + (isLast ? "    " : "│   ");
                renderTree(
                        child,
                        childPrefix,
                        maxDepth,
                        depth + 1,
                        includeFiles,
                        fileMatcher,
                        excludeMatchers,
                        sb,
                        counts,
                        truncated);
            }
        }
    }

    private boolean isExcluded(Path path, List<PathMatcher> matchers) {
        if (matchers.isEmpty()) return false;
        Path name = path.getFileName();
        if (name == null) return false;
        for (PathMatcher m : matchers) {
            if (m.matches(name)) return true;
        }
        return false;
    }

    private List<PathMatcher> buildExcludeMatchers(String excludeStr) {
        List<PathMatcher> matchers = new ArrayList<>();
        if (excludeStr == null || excludeStr.isBlank()) return matchers;
        for (String part : excludeStr.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                matchers.add(FileSystems.getDefault().getPathMatcher("glob:" + trimmed));
            }
        }
        return matchers;
    }

    private Path resolvePath(String pathStr, Path workspaceRoot) {
        String trimmed = pathStr.trim();
        if (trimmed.startsWith("~/")) {
            return Path.of(System.getProperty("user.home")).resolve(trimmed.substring(2));
        }
        if (trimmed.startsWith("/")) {
            return Path.of(trimmed);
        }
        if (trimmed.equals(".")) return workspaceRoot;
        return workspaceRoot.resolve(trimmed);
    }

    private int parseIntOrDefault(Object value, int defaultVal, int min) {
        if (value == null) return defaultVal;
        try {
            int v = Integer.parseInt(value.toString());
            return Math.max(v, min);
        } catch (NumberFormatException ignored) {
            return defaultVal;
        }
    }

    private ToolResult error(String msg) {
        return new ToolResult("tree", msg, true, Map.of());
    }
}
