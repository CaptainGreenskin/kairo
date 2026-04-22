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
package io.kairo.core.tool;

import io.kairo.api.tool.ToolResult;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sanitizes tool results by scanning for output injection patterns and creating error results.
 *
 * <p>Stateless utility extracted from {@link DefaultToolExecutor} pipeline.
 */
public final class ToolResultSanitizer {

    private static final Logger log = LoggerFactory.getLogger(ToolResultSanitizer.class);

    private ToolResultSanitizer() {}

    /**
     * Apply the {@link ToolOutputSanitizer} to a tool result and attach any warnings as metadata.
     *
     * <p>If the scan produces warnings, a new {@link ToolResult} is returned with an {@code
     * "injection_warning"} metadata entry containing the warning list. The original result is
     * returned unchanged when no warnings are found.
     *
     * @param result the original tool result
     * @return the result, possibly enriched with warning metadata
     */
    public static ToolResult sanitize(ToolResult result) {
        if (result.isError()) {
            return result;
        }
        var scanResult = ToolOutputSanitizer.scan(result.content());
        if (!scanResult.hasWarnings()) {
            return result;
        }
        log.warn(
                "Tool '{}' output triggered {} injection warning(s): {}",
                result.toolUseId(),
                scanResult.warnings().size(),
                scanResult.warnings());
        var enrichedMetadata = new HashMap<>(result.metadata());
        enrichedMetadata.put("injection_warning", scanResult.warnings());
        return new ToolResult(
                result.toolUseId(), result.content(), result.isError(), enrichedMetadata);
    }

    /**
     * Create an error {@link ToolResult}.
     *
     * @param toolName the tool name used as the toolUseId
     * @param message the error message
     * @return an error ToolResult
     */
    public static ToolResult errorResult(String toolName, String message) {
        return new ToolResult(toolName, message, true, Map.of());
    }
}
