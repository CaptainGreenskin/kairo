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

import io.kairo.api.tool.SyncTool;
import io.kairo.api.tool.Tool;
import io.kairo.api.tool.ToolCategory;
import io.kairo.api.tool.ToolContext;
import io.kairo.api.tool.ToolOutcome;
import io.kairo.api.tool.ToolOutput;
import io.kairo.api.tool.ToolParam;
import io.kairo.api.tool.ToolResult;
import io.kairo.core.context.recovery.FileAccessTracker;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import reactor.core.publisher.Mono;

/**
 * Reads the contents of a file, optionally within a specific line range.
 *
 * <p>Uses a dual-path strategy for performance:
 *
 * <ul>
 *   <li><b>Fast path</b> (files &lt; {@code KAIRO_READTOOL_FASTPATH_BYTES}, default 1 MB): loads
 *       the entire file into memory via {@link Files#readAllLines}.
 *   <li><b>Streaming path</b> (large files or range requests): uses a {@link BufferedReader} to
 *       skip/read only the requested line range, avoiding full file loading.
 * </ul>
 *
 * <p>When the formatted output exceeds the per-tool output budget configured via {@link
 * io.kairo.api.tool.OutputBudgetConfig}, the result is returned as a {@link ToolOutput.Truncated}
 * envelope.
 */
@Tool(
        name = "read",
        description = "Read the contents of a file. Can read specific line ranges.",
        category = ToolCategory.FILE_AND_CODE)
public class ReadTool implements SyncTool {

    private static final int MAX_LINES_WITHOUT_RANGE = 2000;

    private static final long FAST_PATH_BYTES =
            Long.parseLong(
                    System.getenv().getOrDefault("KAIRO_READTOOL_FASTPATH_BYTES", "1048576"));

    private final FileAccessTracker fileTracker;

    /** Create a ReadTool without file access tracking. */
    public ReadTool() {
        this(null);
    }

    /**
     * Create a ReadTool with optional file access tracking.
     *
     * @param fileTracker the tracker to record file accesses (may be null)
     */
    public ReadTool(FileAccessTracker fileTracker) {
        this.fileTracker = fileTracker;
    }

    @ToolParam(description = "The absolute path of the file to read", required = true)
    private String path;

    @ToolParam(description = "Starting line number (1-based, inclusive)")
    private Integer startLine;

    @ToolParam(description = "Ending line number (1-based, inclusive)")
    private Integer endLine;

    @Override
    public Mono<ToolResult> execute(Map<String, Object> args, ToolContext ctx) {
        return Mono.fromCallable(() -> doExecute(args, ctx));
    }

    private ToolResult doExecute(Map<String, Object> input, ToolContext ctx) {
        String filePath = (String) input.get("path");
        if (filePath == null || filePath.isBlank()) {
            return error("Parameter 'path' is required");
        }

        Path workspaceRoot = ctx.workspace().root();
        Path file = workspaceRoot.resolve(filePath);
        if (!Files.exists(file)) {
            return error("File not found: " + filePath);
        }
        if (!Files.isReadable(file)) {
            return error("Permission denied: cannot read " + filePath);
        }
        if (Files.isDirectory(file)) {
            return error("Path is a directory, not a file: " + filePath);
        }

        try {
            Integer start = toInt(input.get("startLine"));
            Integer end = toInt(input.get("endLine"));
            long fileSize = Files.size(file);

            List<String> lines;
            int totalLines;

            if (start != null || end != null || fileSize >= FAST_PATH_BYTES) {
                // Streaming path: BufferedReader, skip to startLine, read to endLine
                lines = readRange(file, start, end);
                // For streaming path, we need totalLines for metadata
                if (start == null && end == null) {
                    totalLines = lines.size();
                } else {
                    // Count total lines for metadata (lightweight scan)
                    totalLines = countLines(file);
                }
            } else {
                // Fast path: load all
                lines = Files.readAllLines(file, StandardCharsets.UTF_8);
                totalLines = lines.size();
            }

            int startIdx = (start != null) ? Math.max(1, start) : 1;
            int endIdx = (end != null) ? Math.min(totalLines, end) : totalLines;

            // If no range specified and file is very large, suggest using ranges
            if (start == null && end == null && totalLines > MAX_LINES_WITHOUT_RANGE) {
                String content = formatLines(lines, 1, MAX_LINES_WITHOUT_RANGE);
                return ToolResult.success(
                        "read",
                        "File has "
                                + totalLines
                                + " lines which is very large. "
                                + "Showing first "
                                + MAX_LINES_WITHOUT_RANGE
                                + " lines. "
                                + "Use startLine/endLine parameters to read specific sections.\n\n"
                                + content,
                        Map.of("totalLines", totalLines, "path", filePath));
            }

            if (startIdx > totalLines) {
                return error("startLine " + startIdx + " exceeds total lines " + totalLines);
            }

            // For streaming path with range, lines already represent the range
            String formatted;
            if (start != null || end != null) {
                formatted = formatLines(lines, startIdx, startIdx + lines.size() - 1);
            } else {
                formatted = formatLines(lines, startIdx, endIdx);
            }

            // Check if formatted output exceeds per-tool budget
            long budgetMax = ctx.budget().maxPerToolBytes();
            Map<String, Object> metadata =
                    Map.of(
                            "totalLines", totalLines,
                            "path", filePath,
                            "startLine", startIdx,
                            "endLine", endIdx);

            if (formatted.length() > budgetMax) {
                String visible =
                        formatted.substring(0, (int) Math.min(formatted.length(), budgetMax));
                return new ToolResult(
                        "read",
                        new ToolOutput.Truncated(visible, formatted.length(), Optional.empty()),
                        ToolOutcome.SUCCESS,
                        List.of(),
                        metadata);
            }

            if (fileTracker != null) {
                fileTracker.recordAccess(filePath);
            }

            return ToolResult.success("read", formatted, metadata);

        } catch (IOException e) {
            return error("Failed to read file: " + e.getMessage());
        }
    }

    /**
     * Read lines within the specified range using a buffered reader (streaming path). Only loads
     * the requested lines into memory.
     */
    private List<String> readRange(Path path, Integer startLine, Integer endLine)
            throws IOException {
        List<String> result = new ArrayList<>();
        int start = (startLine != null) ? startLine : 1;
        int end = (endLine != null) ? endLine : Integer.MAX_VALUE;
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            int lineNum = 0;
            String line;
            while ((line = reader.readLine()) != null) {
                lineNum++;
                if (lineNum < start) continue;
                if (lineNum > end) break;
                result.add(line);
            }
        }
        return result;
    }

    /** Count total lines in a file without loading all content. */
    private int countLines(Path path) throws IOException {
        int count = 0;
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            while (reader.readLine() != null) {
                count++;
            }
        }
        return count;
    }

    private String formatLines(List<String> lines, int start, int end) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.size() && (start + i) <= end; i++) {
            sb.append(String.format("%6d", start + i))
                    .append("│")
                    .append(lines.get(i))
                    .append('\n');
        }
        return sb.toString();
    }

    private Integer toInt(Object value) {
        if (value == null) return null;
        if (value instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private ToolResult error(String msg) {
        return ToolResult.error("read", msg);
    }
}
