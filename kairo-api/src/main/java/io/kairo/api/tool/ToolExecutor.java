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

import io.kairo.api.tool.UserApprovalHandler;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Executes tools by name with given input parameters. */
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

    /** Clear the allowed tools whitelist, allowing all tools to execute. */
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
}
