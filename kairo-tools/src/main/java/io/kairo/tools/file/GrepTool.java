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
import io.kairo.api.tool.ToolHandler;
import io.kairo.api.tool.ToolParam;
import io.kairo.api.tool.ToolResult;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Searches file contents using regex patterns, similar to ripgrep.
 *
 * <p>Returns matching lines with file paths and line numbers. Supports optional file-glob filtering
 * and limits results to avoid overwhelming the LLM context.
 */
@Tool(
        name = "grep",
        description =
                "Search file contents using regex patterns. Returns matching lines with file paths and line numbers.",
        category = ToolCategory.FILE_AND_CODE)
public class GrepTool implements ToolHandler {

    private static final int MAX_RESULTS = 500;
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
    private static final Set<String> BINARY_EXTENSIONS =
            Set.of(
                    ".jar", ".class", ".png", ".jpg", ".gif", ".ico", ".zip", ".tar", ".gz", ".exe",
                    ".dll", ".so", ".dylib", ".pdf", ".woff", ".woff2", ".ttf", ".eot");

    /**
     * Detects nested quantifiers that can cause catastrophic backtracking (ReDoS). Matches patterns
     * like {@code (a+)+}, {@code (a*)*}, {@code (a+)*}, etc.
     */
    private static final Pattern NESTED_QUANTIFIER =
            Pattern.compile("\\([^)]*[+*][^)]*\\)[+*?]|\\([^)]*[+*][^)]*\\)\\{");

    @ToolParam(description = "The regex pattern to search for", required = true)
    private String pattern;

    @ToolParam(description = "The directory to search in", required = true)
    private String path;

    @ToolParam(description = "File glob filter (e.g. '*.java')")
    private String glob;

    @Override
    public ToolResult execute(Map<String, Object> input) {
        String regexStr = (String) input.get("pattern");
        String searchPath = (String) input.get("path");
        String globFilter = (String) input.get("glob");

        if (regexStr == null || regexStr.isBlank()) {
            return error("Parameter 'pattern' is required");
        }
        if (searchPath == null || searchPath.isBlank()) {
            return error("Parameter 'path' is required");
        }

        Pattern regex;
        try {
            regex = Pattern.compile(regexStr);
        } catch (PatternSyntaxException e) {
            return error("Invalid regex pattern: " + e.getMessage());
        }

        // ReDoS protection: reject patterns with nested quantifiers that can cause
        // catastrophic backtracking (e.g. "(a+)+", "(a*)*", "(a+)*")
        if (regexStr.length() > 500) {
            return error("Regex pattern too long (max 500 characters). Use a simpler pattern.");
        }
        if (NESTED_QUANTIFIER.matcher(regexStr).find()) {
            return error(
                    "Regex pattern contains nested quantifiers which can cause catastrophic "
                            + "backtracking. Please simplify the pattern.");
        }

        Path dir = Path.of(searchPath);
        if (!Files.isDirectory(dir)) {
            return error("Not a directory: " + searchPath);
        }

        PathMatcher globMatcher =
                (globFilter != null && !globFilter.isBlank())
                        ? FileSystems.getDefault().getPathMatcher("glob:" + globFilter)
                        : null;

        List<String> results = new ArrayList<>();

        try {
            Files.walkFileTree(
                    dir,
                    new SimpleFileVisitor<>() {
                        @Override
                        public FileVisitResult preVisitDirectory(
                                Path d, BasicFileAttributes attrs) {
                            if (SKIP_DIRS.contains(d.getFileName().toString())) {
                                return FileVisitResult.SKIP_SUBTREE;
                            }
                            return results.size() < MAX_RESULTS
                                    ? FileVisitResult.CONTINUE
                                    : FileVisitResult.TERMINATE;
                        }

                        @Override
                        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                            if (results.size() >= MAX_RESULTS) {
                                return FileVisitResult.TERMINATE;
                            }
                            // Skip binary files
                            String fileName = file.getFileName().toString();
                            for (String ext : BINARY_EXTENSIONS) {
                                if (fileName.endsWith(ext)) return FileVisitResult.CONTINUE;
                            }
                            // Apply glob filter
                            if (globMatcher != null && !globMatcher.matches(file.getFileName())) {
                                return FileVisitResult.CONTINUE;
                            }

                            try {
                                List<String> lines =
                                        Files.readAllLines(file, StandardCharsets.UTF_8);
                                for (int i = 0;
                                        i < lines.size() && results.size() < MAX_RESULTS;
                                        i++) {
                                    Matcher m = regex.matcher(lines.get(i));
                                    if (m.find()) {
                                        results.add(file + ":" + (i + 1) + ":" + lines.get(i));
                                    }
                                }
                            } catch (IOException e) {
                                // Skip files that can't be read as UTF-8
                            }
                            return FileVisitResult.CONTINUE;
                        }

                        @Override
                        public FileVisitResult visitFileFailed(Path file, IOException exc) {
                            return FileVisitResult.CONTINUE;
                        }
                    });
        } catch (IOException e) {
            return error("Failed to search: " + e.getMessage());
        }

        if (results.isEmpty()) {
            return new ToolResult(
                    "grep",
                    "No matches found for pattern: " + regexStr,
                    false,
                    Map.of("count", 0, "pattern", regexStr));
        }

        boolean truncated = results.size() >= MAX_RESULTS;
        String content = String.join("\n", results);
        if (truncated) {
            content += "\n... (truncated at " + MAX_RESULTS + " results)";
        }

        return new ToolResult(
                "grep",
                content,
                false,
                Map.of("count", results.size(), "pattern", regexStr, "truncated", truncated));
    }

    private ToolResult error(String msg) {
        return new ToolResult("grep", msg, true, Map.of());
    }
}
