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
import io.kairo.api.tool.ToolParam;
import io.kairo.api.tool.ToolResult;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import reactor.core.publisher.Mono;

/**
 * Reads multiple files in a single tool call, reducing ReAct loop iterations.
 *
 * <p>Each file is delimited by a header line. A single file failure does not abort the batch.
 */
@Tool(
        name = "batch_read",
        description =
                "Read up to 20 files in a single call. Returns each file's content separated by"
                        + " headers. Missing or unreadable files are marked with [ERROR: reason]"
                        + " without aborting the batch.",
        category = ToolCategory.FILE_AND_CODE)
public class BatchReadTool implements SyncTool {

    private static final int MAX_FILES = 20;
    private static final int DEFAULT_MAX_LINES = 500;

    @ToolParam(description = "JSON array of absolute file paths to read (max 20)", required = true)
    private List<String> paths;

    @ToolParam(description = "Maximum lines to read per file (default 500)")
    private Integer maxLinesPerFile;

    @Override
    public Mono<ToolResult> execute(Map<String, Object> args, ToolContext ctx) {
        return Mono.fromCallable(() -> doExecute(args, ctx.workspace().root()));
    }

    private ToolResult doExecute(Map<String, Object> input, Path workspaceRoot) {
        Object rawPaths = input.get("paths");
        if (!(rawPaths instanceof List<?> pathList) || pathList.isEmpty()) {
            return error("Parameter 'paths' must be a non-empty JSON array of file paths");
        }
        if (pathList.size() > MAX_FILES) {
            return error("Too many files: maximum is " + MAX_FILES + ", got " + pathList.size());
        }

        int maxLines =
                input.get("maxLinesPerFile") instanceof Number n ? n.intValue() : DEFAULT_MAX_LINES;
        if (maxLines <= 0) {
            maxLines = DEFAULT_MAX_LINES;
        }

        StringBuilder result = new StringBuilder();
        int successCount = 0;
        int errorCount = 0;

        for (Object rawPath : pathList) {
            String filePath = rawPath != null ? rawPath.toString().trim() : "";
            result.append("=== ").append(filePath).append(" ===\n");

            if (filePath.isEmpty()) {
                result.append("[ERROR: empty path]\n\n");
                errorCount++;
                continue;
            }

            try {
                Path file = workspaceRoot.resolve(filePath);
                if (!Files.exists(file)) {
                    result.append("[ERROR: file not found]\n\n");
                    errorCount++;
                    continue;
                }
                if (Files.isDirectory(file)) {
                    result.append("[ERROR: path is a directory]\n\n");
                    errorCount++;
                    continue;
                }
                if (!Files.isReadable(file)) {
                    result.append("[ERROR: permission denied]\n\n");
                    errorCount++;
                    continue;
                }

                List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
                int total = lines.size();
                int limit = Math.min(total, maxLines);
                for (int i = 0; i < limit; i++) {
                    result.append(lines.get(i)).append('\n');
                }
                if (limit < total) {
                    result.append(
                            "[... truncated at "
                                    + maxLines
                                    + " lines, total "
                                    + total
                                    + " lines]\n");
                }
                result.append('\n');
                successCount++;

            } catch (IOException e) {
                result.append("[ERROR: ").append(e.getMessage()).append("]\n\n");
                errorCount++;
            }
        }

        return ToolResult.success(
                "batch_read",
                result.toString().stripTrailing(),
                Map.of("successCount", successCount, "errorCount", errorCount));
    }

    private ToolResult error(String msg) {
        return ToolResult.error("batch_read", msg);
    }
}
