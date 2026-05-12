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

/**
 * Sealed hierarchy of events emitted during streaming tool execution.
 *
 * @since 1.2.0
 */
public sealed interface ToolEvent {

    /**
     * A streaming output chunk.
     *
     * @param data the chunk content
     * @param kind the stream kind (stdout or stderr)
     */
    record Chunk(String data, StreamKind kind) implements ToolEvent {}

    /**
     * Progress indicator for long-running tools.
     *
     * @param pct progress fraction: 0.0–1.0 = determinate; -1.0 = indeterminate
     * @param message human-readable progress message
     */
    record Progress(double pct, String message) implements ToolEvent {}

    /**
     * Signals that the tool needs human approval to proceed.
     *
     * @param description what the tool wants to do
     * @param reason why approval is needed
     */
    record NeedsApproval(String description, String reason) implements ToolEvent {}

    /**
     * Terminal event carrying the final result.
     *
     * @param result the completed tool result
     */
    record Final(ToolResult result) implements ToolEvent {}

    /** Classification of streaming output channels. */
    enum StreamKind {
        STDOUT,
        STDERR
    }
}
