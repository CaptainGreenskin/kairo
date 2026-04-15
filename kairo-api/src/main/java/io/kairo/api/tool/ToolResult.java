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

/**
 * Result of a tool execution.
 *
 * @param toolUseId the ID correlating to the original tool-use request
 * @param content the textual result content
 * @param isError whether the execution resulted in an error
 * @param metadata additional metadata about the execution
 */
public record ToolResult(
        String toolUseId, String content, boolean isError, Map<String, Object> metadata) {}
