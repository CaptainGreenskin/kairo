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
import io.kairo.api.tool.ToolParam;
import io.kairo.api.tool.ToolResult;
import io.kairo.api.tool.ToolSideEffect;
import io.kairo.core.context.recovery.FileAccessTracker;
import io.kairo.core.tool.ToolHandler;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Makes precise text replacements in a file.
 *
 * <p>The {@code originalText} must be unique within the file. If not found exactly, a trimmed
 * comparison is attempted as a fallback. Multiple matches cause an error to avoid ambiguous edits.
 */
@Tool(
        name = "edit",
        description =
                "Make precise text replacements in a file. The original text must be unique in the file.",
        category = ToolCategory.FILE_AND_CODE,
        sideEffect = ToolSideEffect.WRITE)
public class EditTool implements ToolHandler {

    private final FileAccessTracker fileTracker;

    /** Create an EditTool without file access tracking. */
    public EditTool() {
        this(null);
    }

    /**
     * Create an EditTool with optional file access tracking.
     *
     * @param fileTracker the tracker to record file accesses (may be null)
     */
    public EditTool(FileAccessTracker fileTracker) {
        this.fileTracker = fileTracker;
    }

    @ToolParam(description = "The absolute path of the file to edit", required = true)
    private String path;

    @ToolParam(description = "The exact text to find and replace", required = true)
    private String originalText;

    @ToolParam(description = "The replacement text", required = true)
    private String newText;

    @Override
    public ToolResult execute(Map<String, Object> input) {
        String filePath = (String) input.get("path");
        String original = (String) input.get("originalText");
        String replacement = (String) input.get("newText");

        if (filePath == null || filePath.isBlank()) {
            return error("Parameter 'path' is required");
        }
        if (original == null) {
            return error("Parameter 'originalText' is required");
        }
        if (replacement == null) {
            return error("Parameter 'newText' is required");
        }

        Path file = Path.of(filePath);
        if (!Files.exists(file)) {
            return error("File not found: " + filePath);
        }

        try {
            String content = Files.readString(file, StandardCharsets.UTF_8);

            // Exact match
            int count = countOccurrences(content, original);
            if (count == 1) {
                String updated = content.replace(original, replacement);
                Files.writeString(file, updated, StandardCharsets.UTF_8);
                if (fileTracker != null) {
                    fileTracker.recordAccess(filePath);
                }
                return new ToolResult(
                        "edit", "Successfully edited " + filePath, false, Map.of("path", filePath));
            }

            if (count > 1) {
                return error(
                        "Found "
                                + count
                                + " occurrences of the original text. "
                                + "Please provide more context to make the match unique.");
            }

            // Fallback: trimmed comparison (normalize whitespace per line)
            String trimmedOriginal = original.strip();
            String trimmedContent = content.strip();
            if (trimmedContent.contains(trimmedOriginal)) {
                int trimCount = countOccurrences(content, trimmedOriginal);
                if (trimCount == 1) {
                    String updated = content.replace(trimmedOriginal, replacement);
                    Files.writeString(file, updated, StandardCharsets.UTF_8);
                    if (fileTracker != null) {
                        fileTracker.recordAccess(filePath);
                    }
                    return new ToolResult(
                            "edit",
                            "Successfully edited "
                                    + filePath
                                    + " (matched after trimming whitespace)",
                            false,
                            Map.of("path", filePath));
                }
            }

            return error(
                    "Could not find the specified text in "
                            + filePath
                            + ". Please verify the original text matches exactly.");

        } catch (IOException e) {
            return error("Failed to edit file: " + e.getMessage());
        }
    }

    private int countOccurrences(String text, String search) {
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(search, idx)) != -1) {
            count++;
            idx += search.length();
        }
        return count;
    }

    private ToolResult error(String msg) {
        return new ToolResult("edit", msg, true, Map.of());
    }
}
