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

import io.kairo.api.agent.CancellationSignal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Executes tools by name with given input parameters.
 *
 * <p>The executor is the central dispatch point for tool invocations within an agent's acting
 * phase. It resolves tool handlers, enforces permission policies (via {@link UserApprovalHandler}),
 * manages circuit breakers for fault tolerance, and optionally restricts execution to an
 * allowed-tools whitelist.
 *
 * <p>Implementations should be thread-safe: parallel tool execution via {@link
 * #executeParallel(List)} may invoke multiple handlers concurrently.
 *
 * <p><strong>Cooperative cancellation:</strong> implementations should observe {@link
 * CancellationSignal} from Reactor Context (key: {@link CancellationSignal#CONTEXT_KEY}) and
 * terminate long-running tool work promptly when cancelled.
 *
 * @apiNote Stable SPI — backward compatible across minor versions. Breaking changes only in major
 *     versions with 2-minor-version deprecation notice.
 * @implSpec Implementations must be thread-safe: parallel tool execution via {@link
 *     #executeParallel(List)} may invoke multiple handlers concurrently. Observe {@link
 *     CancellationSignal} from Reactor Context and terminate long-running tool work promptly when
 *     cancelled. New {@code default} methods may be added in minor versions to preserve backward
 *     compatibility.
 * @see ToolResult
 * @see ToolInvocation
 * @see ToolSideEffect
 * @since 0.1.0
 */
public interface ToolExecutor {

    /**
     * Execute a tool with the given input.
     *
     * @param toolName the name of the tool to execute
     * @param input the input parameters
     * @return a Mono emitting the tool result
     */
    Mono<ToolResult> execute(String toolName, Map<String, Object> input);

    /**
     * Execute a tool with a timeout.
     *
     * @param toolName the tool name
     * @param input the input parameters
     * @param timeout the maximum execution duration
     * @return a Mono emitting the tool result
     */
    Mono<ToolResult> execute(String toolName, Map<String, Object> input, Duration timeout);

    /**
     * Execute multiple tool invocations in parallel.
     *
     * @param invocations the tool invocations to execute
     * @return a Flux emitting results as they complete
     */
    Flux<ToolResult> executeParallel(List<ToolInvocation> invocations);

    /**
     * Set allowed tools whitelist for skill-based restrictions. When set, only tools in this set
     * can be executed.
     *
     * @param tools set of tool names that are allowed
     */
    default void setAllowedTools(Set<String> tools) {}

    /**
     * Clear the allowed tools whitelist, restoring unrestricted tool execution.
     *
     * <p>After this call, all registered tools are eligible for execution regardless of any prior
     * {@link #setAllowedTools(Set)} restriction.
     */
    default void clearAllowedTools() {}

    /**
     * Register a tool instance by name into this executor. Used by MCP and other dynamic tool
     * sources to inject executable handlers at runtime.
     *
     * @param toolName the tool name
     * @param instance the tool handler instance
     */
    default void registerToolInstance(String toolName, Object instance) {}

    /**
     * Set metadata for a tool. Metadata is passed to guardrail policies via the guardrail context.
     *
     * @param toolName the tool name
     * @param metadata the metadata key-value pairs
     */
    default void setToolMetadata(String toolName, java.util.Map<String, Object> metadata) {}

    /**
     * Whether this executor supports streaming tool execution.
     *
     * @return true if streaming dispatch is supported
     */
    default boolean supportsStreaming() {
        return false;
    }

    /**
     * Resolve the side-effect classification for a tool by name. Defaults to {@link
     * ToolSideEffect#SYSTEM_CHANGE} (safest assumption) when the tool is unknown.
     *
     * @param toolName the tool name
     * @return the side-effect classification
     */
    default ToolSideEffect resolveSideEffect(String toolName) {
        return ToolSideEffect.SYSTEM_CHANGE;
    }

    /**
     * Execute a single tool invocation through the permission and approval pipeline.
     *
     * @param invocation the tool invocation to execute
     * @return a Mono emitting the tool result
     */
    default Mono<ToolResult> executeSingle(ToolInvocation invocation) {
        return execute(invocation.toolName(), invocation.input());
    }

    /**
     * Set the approval handler for tool execution requiring human approval.
     *
     * @param approvalHandler the approval handler, or null to disable approval flow
     */
    default void setApprovalHandler(UserApprovalHandler approvalHandler) {}

    /**
     * Reset circuit breaker state for all tools, allowing previously tripped tools to be retried.
     */
    default void resetCircuitBreaker() {}

    /**
     * Reset circuit breaker state for a specific tool.
     *
     * @param toolName the name of the tool whose circuit breaker should be reset
     */
    default void resetCircuitBreaker(String toolName) {}
}
