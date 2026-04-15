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
 * Creates or overwrites a file with the given content.
 *
 * <p>Automatically creates parent directories if they do not exist. The file is written using UTF-8
 * encoding.
 */
@Tool(
        name = "write",
        description =
                "Create or overwrite a file with the given content. Automatically creates parent directories.",
        category = ToolCategory.FILE_AND_CODE,
        sideEffect = ToolSideEffect.WRITE)
public class WriteTool implements ToolHandler {

    private final FileAccessTracker fileTracker;

    /** Create a WriteTool without file access tracking. */
    public WriteTool() {
        this(null);
    }

    /**
     * Create a WriteTool with optional file access tracking.
     *
     * @param fileTracker the tracker to record file accesses (may be null)
     */
    public WriteTool(FileAccessTracker fileTracker) {
        this.fileTracker = fileTracker;
    }

    @ToolParam(description = "The absolute path of the file to write", required = true)
    private String path;

    @ToolParam(description = "The content to write to the file", required = true)
    private String content;

    @Override
    public ToolResult execute(Map<String, Object> input) {
        String filePath = (String) input.get("path");
        String fileContent = (String) input.get("content");

        if (filePath == null || filePath.isBlank()) {
            return error("Parameter 'path' is required");
        }
        if (fileContent == null) {
            return error("Parameter 'content' is required");
        }

        Path file = Path.of(filePath);
        try {
            // Create parent directories if needed
            Path parent = file.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }

            byte[] bytes = fileContent.getBytes(StandardCharsets.UTF_8);
            Files.write(file, bytes);

            if (fileTracker != null) {
                fileTracker.recordAccess(filePath);
            }

            return new ToolResult(
                    "write",
                    "Successfully wrote " + bytes.length + " bytes to " + filePath,
                    false,
                    Map.of("path", filePath, "bytesWritten", bytes.length));

        } catch (IOException e) {
            return error("Failed to write file: " + e.getMessage());
        }
    }

    private ToolResult error(String msg) {
        return new ToolResult("write", msg, true, Map.of());
    }
}
