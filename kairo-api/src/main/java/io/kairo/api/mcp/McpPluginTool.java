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
package io.kairo.api.mcp;

import io.kairo.api.Stable;
import io.kairo.api.tool.ToolDefinition;

/**
 * A discovered MCP tool binding.
 *
 * @param definition normalized tool definition exposed to the runtime
 * @param executor executable instance used by {@code ToolExecutor.registerToolInstance}
 */
@Stable(value = "MCP plugin tool binding; shape frozen since v0.4", since = "1.0.0")
public record McpPluginTool(ToolDefinition definition, Object executor) {}
