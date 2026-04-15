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

import java.time.Duration;

/**
 * Runtime description of a registered tool.
 *
 * @param name the tool name
 * @param description a description of what the tool does
 * @param category the tool category
 * @param inputSchema JSON schema describing the tool's input parameters
 * @param implementationClass the class that implements this tool
 * @param timeout per-tool timeout, or null to use the executor default
 * @param sideEffect the side-effect classification of this tool
 */
public record ToolDefinition(
        String name,
        String description,
        ToolCategory category,
        JsonSchema inputSchema,
        Class<?> implementationClass,
        Duration timeout,
        ToolSideEffect sideEffect) {

    /** Backward-compatible constructor without timeout and sideEffect. */
    public ToolDefinition(
            String name,
            String description,
            ToolCategory category,
            JsonSchema inputSchema,
            Class<?> implementationClass) {
        this(
                name,
                description,
                category,
                inputSchema,
                implementationClass,
                null,
                ToolSideEffect.READ_ONLY);
    }

    /** Backward-compatible constructor without sideEffect. */
    public ToolDefinition(
            String name,
            String description,
            ToolCategory category,
            JsonSchema inputSchema,
            Class<?> implementationClass,
            Duration timeout) {
        this(
                name,
                description,
                category,
                inputSchema,
                implementationClass,
                timeout,
                ToolSideEffect.READ_ONLY);
    }
}
