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

import java.util.Map;
import reactor.core.publisher.Mono;

/**
 * A tool that executes synchronously (request → single response).
 *
 * <p>The {@link ToolExecutor} wraps the returned {@link Mono} into a single {@link ToolEvent.Final}
 * event for streaming dispatch.
 *
 * @since 1.2.0
 */
public interface SyncTool {

    /**
     * Execute this tool with the given arguments and context.
     *
     * @param args the input parameters parsed from the LLM's tool-use request
     * @param ctx the runtime context (agent ID, session ID, dependencies, budget, etc.)
     * @return a Mono emitting the tool result
     */
    Mono<ToolResult> execute(Map<String, Object> args, ToolContext ctx);

    /**
     * Optionally declare the JSON schema for this tool's inputs.
     *
     * <p>The default implementation returns {@code null}, which signals the framework to derive a
     * schema by reflecting over {@link ToolParam}-annotated fields.
     *
     * @return the input schema, or {@code null} to fall back to field-based scanning
     */
    default JsonSchema inputSchema() {
        return null;
    }
}
