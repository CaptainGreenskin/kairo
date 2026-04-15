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
package io.kairo.api.tracing;

import io.kairo.api.message.Msg;
import io.kairo.api.model.ModelResponse;
import io.kairo.api.tool.ToolResult;
import java.util.Map;
import java.util.function.Supplier;
import reactor.core.publisher.Mono;

/**
 * SPI for observability and tracing across the Kairo runtime.
 *
 * <p>This provides interception points for
 * agent calls, model calls, and tool executions. Implementations can integrate with OpenTelemetry,
 * Micrometer, or any other observability stack.
 *
 * <p>Default methods are no-ops, so implementations only need to override what they care about.
 */
public interface Tracer {

    /**
     * Wrap an agent call for tracing.
     *
     * @param agentName the agent name
     * @param input the input message
     * @param agentCall the actual agent call supplier
     * @return the traced Mono
     */
    default Mono<Msg> traceAgentCall(String agentName, Msg input, Supplier<Mono<Msg>> agentCall) {
        return agentCall.get();
    }

    /**
     * Wrap a model API call for tracing.
     *
     * @param providerName the model provider name
     * @param messageCount number of messages in the request
     * @param modelCall the actual model call supplier
     * @return the traced Mono
     */
    default Mono<ModelResponse> traceModelCall(
            String providerName, int messageCount, Supplier<Mono<ModelResponse>> modelCall) {
        return modelCall.get();
    }

    /**
     * Wrap a tool execution for tracing.
     *
     * @param toolName the tool name
     * @param input the tool input
     * @param toolCall the actual tool call supplier
     * @return the traced Mono
     */
    default Mono<ToolResult> traceToolCall(
            String toolName, Map<String, Object> input, Supplier<Mono<ToolResult>> toolCall) {
        return toolCall.get();
    }

    /**
     * Record a compaction event.
     *
     * @param tokensSaved tokens freed by compaction
     * @param pressureBefore pressure before compaction
     * @param pressureAfter pressure after compaction
     */
    default void recordCompaction(int tokensSaved, float pressureBefore, float pressureAfter) {}

    /**
     * Record an agent iteration.
     *
     * @param agentName the agent name
     * @param iteration the iteration number
     * @param tokensUsed total tokens used so far
     */
    default void recordIteration(String agentName, int iteration, int tokensUsed) {}
}
