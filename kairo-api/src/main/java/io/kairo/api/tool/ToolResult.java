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
package io.kairo.api.tool;

import io.kairo.api.Stable;
import java.util.List;
import java.util.Map;

/**
 * Result of a tool execution, combining output, outcome classification, hints, and metadata.
 *
 * <p>This is the v1.2 redesign of the original flat {@code (toolUseId, content, isError, metadata)}
 * record. Convenience factories {@link #success(String, String)} and {@link #error(String, String)}
 * provide backward-compatible creation paths during the migration period.
 *
 * @param toolUseId the ID correlating to the original tool-use request
 * @param output the structured output of the tool execution
 * @param outcome the outcome classification (SUCCESS, ERROR, CANCELLED, TIMEOUT)
 * @param hints actionable hints for the agent or user
 * @param metadata additional metadata about the execution
 * @since 1.2.0
 */
@Stable(value = "Tool execution result record; BREAKING redesign in v1.2 from 1.0.0", since = "1.2.0")
public record ToolResult(
        String toolUseId,
        ToolOutput output,
        ToolOutcome outcome,
        List<Hint> hints,
        Map<String, Object> metadata) {

    /** Create a successful text result with no hints or metadata. */
    public static ToolResult success(String toolUseId, String content) {
        return new ToolResult(
                toolUseId, new ToolOutput.Text(content), ToolOutcome.SUCCESS, List.of(), Map.of());
    }

    /** Create an error text result with no hints or metadata. */
    public static ToolResult error(String toolUseId, String message) {
        return new ToolResult(
                toolUseId, new ToolOutput.Text(message), ToolOutcome.ERROR, List.of(), Map.of());
    }

    /** Create a successful text result with metadata. */
    public static ToolResult success(
            String toolUseId, String content, Map<String, Object> metadata) {
        return new ToolResult(
                toolUseId, new ToolOutput.Text(content), ToolOutcome.SUCCESS, List.of(), metadata);
    }

    /** Create an error text result with hints. */
    public static ToolResult error(String toolUseId, String message, List<Hint> hints) {
        return new ToolResult(
                toolUseId, new ToolOutput.Text(message), ToolOutcome.ERROR, hints, Map.of());
    }

    /** Create an error text result with metadata. */
    public static ToolResult error(String toolUseId, String message, Map<String, Object> metadata) {
        return new ToolResult(
                toolUseId, new ToolOutput.Text(message), ToolOutcome.ERROR, List.of(), metadata);
    }

    /**
     * Backward-compatible factory bridging the old {@code (toolUseId, content, isError, metadata)}
     * constructor pattern to the new record layout. Use {@link #success} or {@link #error} directly
     * when the outcome is known at compile time.
     *
     * @deprecated Prefer {@link #success} or {@link #error} factories for clarity.
     */
    @Deprecated
    public static ToolResult of(
            String toolUseId, String content, boolean isError, Map<String, Object> metadata) {
        ToolOutcome outcome = isError ? ToolOutcome.ERROR : ToolOutcome.SUCCESS;
        Map<String, Object> safeMeta = metadata == null ? Map.of() : metadata;
        return new ToolResult(
                toolUseId, new ToolOutput.Text(content), outcome, List.of(), safeMeta);
    }

    /**
     * Returns the text content if the output is a {@link ToolOutput.Text}, or a toString
     * representation otherwise. Convenience accessor for backward compatibility.
     */
    public String content() {
        if (output instanceof ToolOutput.Text text) {
            return text.content();
        }
        return output.toString();
    }

    /**
     * Returns whether this result represents an error. Convenience accessor for backward
     * compatibility.
     */
    public boolean isError() {
        return outcome != ToolOutcome.SUCCESS;
    }
}
