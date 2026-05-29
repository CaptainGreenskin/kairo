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
package io.kairo.core.agent;

import java.util.Map;

/**
 * Optional Reactor-Context callback invoked once per completed tool call, carrying the tool name,
 * input args, and result. Mirrors {@link ReasoningPhase#THINKING_DELTA_KEY}: a caller publishes a
 * sink under {@link #CONTEXT_KEY} via {@code Mono#contextWrite}, and {@link ToolPhase} reads it
 * deep inside the running ReAct loop to stream each tool call out.
 *
 * <p>Primary use: the expert-team coordinator threads a per-step sink into the worker agent so the
 * UI can show each expert's real read/edit/bash calls instead of a single opaque invocation. The
 * callback is best-effort and invoked on the tool-execution thread — it must not block or throw
 * (ToolPhase swallows exceptions).
 *
 * @since v1.3 (Experimental, internal)
 */
@FunctionalInterface
public interface ToolCallSink {

    /** Reactor Context key under which a {@link ToolCallSink} may be published. */
    String CONTEXT_KEY = "kairo.tool-call-sink";

    /**
     * Invoked after a tool call completes (success or error).
     *
     * @param toolName the tool that was invoked
     * @param args the tool input arguments
     * @param result the tool result content (output text)
     * @param isError whether the tool reported an error
     * @param durationMs wall-clock execution time in milliseconds
     */
    void onToolCall(
            String toolName,
            Map<String, Object> args,
            String result,
            boolean isError,
            long durationMs);
}
