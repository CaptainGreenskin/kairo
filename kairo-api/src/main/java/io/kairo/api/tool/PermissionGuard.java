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
 * Guards tool execution by checking permissions before invocation.
 *
 * <p>Implementations may check file paths, command patterns, or other safety constraints before
 * allowing a tool to run. Inspired by claude-code-best's multi-layer permission model, guards
 * should check not just shell commands but also file write paths and other sensitive operations.
 */
public interface PermissionGuard {

    /**
     * Check whether the given tool invocation is permitted.
     *
     * @param toolName the tool name
     * @param input the tool input parameters
     * @return a Mono emitting true if permitted, false otherwise
     */
    Mono<Boolean> checkPermission(String toolName, Map<String, Object> input);

    /**
     * Check permission with tool category context for more granular control.
     *
     * @param toolName the tool name
     * @param category the tool category
     * @param input the tool input parameters
     * @return a Mono emitting true if permitted, false otherwise
     */
    default Mono<Boolean> checkPermission(
            String toolName, ToolCategory category, Map<String, Object> input) {
        return checkPermission(toolName, input);
    }

    /**
     * Check permission with structured decision including reason and policy context.
     *
     * @param toolName the tool name
     * @param input the tool input parameters
     * @return a Mono emitting the structured permission decision
     */
    default Mono<PermissionDecision> checkPermissionDetail(
            String toolName, Map<String, Object> input) {
        return checkPermission(toolName, input)
                .map(
                        allowed ->
                                allowed
                                        ? PermissionDecision.allow()
                                        : PermissionDecision.deny("Denied by guard", "default"));
    }

    /**
     * Check permission with structured decision, including tool category context.
     *
     * @param toolName the tool name
     * @param category the tool category
     * @param input the tool input parameters
     * @return a Mono emitting the structured permission decision
     */
    default Mono<PermissionDecision> checkPermissionDetail(
            String toolName, ToolCategory category, Map<String, Object> input) {
        return checkPermissionDetail(toolName, input);
    }

    /**
     * Register a dangerous pattern that should be blocked.
     *
     * @param pattern a regex or glob pattern to match against tool inputs
     */
    void addDangerousPattern(String pattern);

    /**
     * Register a sensitive file path pattern that write tools should not touch.
     *
     * @param pathPattern a regex pattern matching sensitive file paths
     */
    default void addSensitivePathPattern(String pathPattern) {
        // Default no-op for backward compatibility
    }
}
