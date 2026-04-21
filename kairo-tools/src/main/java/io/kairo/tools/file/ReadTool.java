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
import io.kairo.core.context.recovery.FileAccessTracker;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Reads the contents of a file, optionally within a specific line range.
 *
 * <p>For large files (over 2000 lines), the tool will suggest using line ranges to avoid
 * overwhelming the LLM context window.
 */
@Tool(
        name = "read",
        description = "Read the contents of a file. Can read specific line ranges.",
        category = ToolCategory.FILE_AND_CODE)
public class ReadTool implements ToolHandler {

    private static final int MAX_LINES_WITHOUT_RANGE = 2000;
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
    public ToolResult execute(Map<String, Object> input) {
        String filePath = (String) input.get("path");
        if (filePath == null || filePath.isBlank()) {
            return error("Parameter 'path' is required");
        }

        Path file = Path.of(filePath);
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
            List<String> allLines = Files.readAllLines(file, StandardCharsets.UTF_8);
            int totalLines = allLines.size();

            Integer start = toInt(input.get("startLine"));
            Integer end = toInt(input.get("endLine"));

            // If no range specified and file is very large, suggest using ranges
            if (start == null && end == null && totalLines > MAX_LINES_WITHOUT_RANGE) {
                return new ToolResult(
                        "read",
                        "File has "
                                + totalLines
                                + " lines which is very large. "
                                + "Showing first "
                                + MAX_LINES_WITHOUT_RANGE
                                + " lines. "
                                + "Use startLine/endLine parameters to read specific sections.\n\n"
                                + formatLines(allLines, 1, MAX_LINES_WITHOUT_RANGE),
                        false,
                        Map.of("totalLines", totalLines, "path", filePath));
            }

            int startIdx = (start != null) ? Math.max(1, start) : 1;
            int endIdx = (end != null) ? Math.min(totalLines, end) : totalLines;

            if (startIdx > totalLines) {
                return error("startLine " + startIdx + " exceeds total lines " + totalLines);
            }

            String content = formatLines(allLines, startIdx, endIdx);

            if (fileTracker != null) {
                fileTracker.recordAccess(filePath);
            }

            return new ToolResult(
                    "read",
                    content,
                    false,
                    Map.of(
                            "totalLines",
                            totalLines,
                            "path",
                            filePath,
                            "startLine",
                            startIdx,
                            "endLine",
                            endIdx));

        } catch (IOException e) {
            return error("Failed to read file: " + e.getMessage());
        }
    }

    private String formatLines(List<String> lines, int start, int end) {
        StringBuilder sb = new StringBuilder();
        for (int i = start - 1; i < end && i < lines.size(); i++) {
            sb.append(String.format("%6d", i + 1)).append("│").append(lines.get(i)).append('\n');
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
        return new ToolResult("read", msg, true, Map.of());
    }
}
