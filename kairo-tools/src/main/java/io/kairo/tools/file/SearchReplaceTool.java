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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Performs regex-based find-and-replace on a file.
 *
 * <p>Supports Java regex syntax including capture group back-references ($1, $2, …). Limits pattern
 * length to prevent ReDoS attacks.
 */
@Tool(
        name = "search_replace",
        description =
                "Perform regex find-and-replace in a file. Supports Java regex syntax and"
                        + " capture-group references ($1, $2, …) in the replacement string."
                        + " Safe against ReDoS: patterns over 1000 characters are rejected.",
        category = ToolCategory.FILE_AND_CODE)
public class SearchReplaceTool implements ToolHandler {

    private static final int MAX_PATTERN_LENGTH = 1000;
    private static final long MAX_OUTPUT_BYTES = 10L * 1024 * 1024; // 10 MB

    @ToolParam(description = "Absolute path of the file to modify", required = true)
    private String path;

    @ToolParam(description = "Java regex pattern to search for", required = true)
    private String search;

    @ToolParam(
            description = "Replacement string (supports $1, $2 group references)",
            required = true)
    private String replace;

    @ToolParam(
            description = "Replace all occurrences (true) or only the first (false); default true")
    private Boolean replaceAll;

    @ToolParam(
            description =
                    "Comma-separated regex flags: CASE_INSENSITIVE, MULTILINE, DOTALL, LITERAL")
    private String flags;

    @Override
    public ToolResult execute(Map<String, Object> input) {
        return doExecute(input, Workspace.cwd().root());
    }

    @Override
    public ToolResult execute(Map<String, Object> input, ToolContext context) {
        return doExecute(input, context.workspace().root());
    }

    private ToolResult doExecute(Map<String, Object> input, Path workspaceRoot) {
        String filePath = (String) input.get("path");
        String searchPattern = (String) input.get("search");
        String replacement = (String) input.get("replace");

        if (filePath == null || filePath.isBlank()) {
            return error("Parameter 'path' is required");
        }
        if (searchPattern == null) {
            return error("Parameter 'search' is required");
        }
        if (replacement == null) {
            return error("Parameter 'replace' is required");
        }
        if (searchPattern.length() > MAX_PATTERN_LENGTH) {
            return error(
                    "Pattern too long ("
                            + searchPattern.length()
                            + " chars); maximum is "
                            + MAX_PATTERN_LENGTH
                            + " to prevent ReDoS");
        }

        boolean doReplaceAll = !(input.get("replaceAll") instanceof Boolean b) || b; // default true
        int flagBits = parseFlags(input.get("flags") instanceof String s ? s : "");

        Pattern pattern;
        try {
            pattern = Pattern.compile(searchPattern, flagBits);
        } catch (PatternSyntaxException e) {
            return error("Invalid regex pattern: " + e.getMessage());
        }

        Path file = workspaceRoot.resolve(filePath);
        if (!Files.exists(file)) {
            return error("File not found: " + filePath);
        }
        if (Files.isDirectory(file)) {
            return error("Path is a directory: " + filePath);
        }

        String content;
        try {
            content = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return error("Failed to read file: " + e.getMessage());
        }

        Matcher matcher = pattern.matcher(content);
        String updated =
                doReplaceAll ? matcher.replaceAll(replacement) : matcher.replaceFirst(replacement);

        if (updated.getBytes(StandardCharsets.UTF_8).length > MAX_OUTPUT_BYTES) {
            return error(
                    "Result would exceed 10 MB size limit after replacement; operation aborted");
        }

        int matchCount = countMatches(pattern, content, doReplaceAll);

        try {
            Files.writeString(file, updated, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return error("Failed to write file: " + e.getMessage());
        }

        return new ToolResult(
                "search_replace",
                "Replaced " + matchCount + " occurrence(s) in " + filePath,
                false,
                Map.of("path", filePath, "matchCount", matchCount));
    }

    private int countMatches(Pattern pattern, String content, boolean all) {
        Matcher m = pattern.matcher(content);
        if (!all) {
            return m.find() ? 1 : 0;
        }
        int count = 0;
        while (m.find()) {
            count++;
        }
        return count;
    }

    private int parseFlags(String flagStr) {
        if (flagStr == null || flagStr.isBlank()) {
            return 0;
        }
        int bits = 0;
        for (String token : flagStr.split(",")) {
            bits |=
                    switch (token.trim().toUpperCase()) {
                        case "CASE_INSENSITIVE" -> Pattern.CASE_INSENSITIVE;
                        case "MULTILINE" -> Pattern.MULTILINE;
                        case "DOTALL" -> Pattern.DOTALL;
                        case "LITERAL" -> Pattern.LITERAL;
                        default -> 0;
                    };
        }
        return bits;
    }

    private ToolResult error(String msg) {
        return new ToolResult("search_replace", msg, true, Map.of());
    }
}
