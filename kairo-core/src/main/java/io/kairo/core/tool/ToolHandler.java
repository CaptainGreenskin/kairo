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
import java.util.Map;

/**
 * Base interface that all tool implementations must implement.
 *
 * <p>Each tool class annotated with {@link io.kairo.api.tool.Tool @Tool} should implement this
 * interface. The {@link DefaultToolExecutor} invokes {@link #execute(Map)} to run the tool logic.
 */
public interface ToolHandler {

    /**
     * Execute this tool with the given input parameters.
     *
     * @param input the input parameters parsed from the LLM's tool-use request
     * @return the result of the tool execution
     * @throws Exception if the tool execution fails
     */
    ToolResult execute(Map<String, Object> input) throws Exception;
}
