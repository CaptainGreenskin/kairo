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

import io.kairo.api.Stable;

/** Categorization for tools, used for filtering and grouping. */
@Stable(value = "Tool category enum; values frozen since v0.1", since = "1.0.0")
public enum ToolCategory {

    /** File and code manipulation tools (read, write, search, edit). */
    FILE_AND_CODE,

    /** Command execution and process management. */
    EXECUTION,

    /** Information retrieval (web search, documentation). */
    INFORMATION,

    /** Agent and task management tools. */
    AGENT_AND_TASK,

    /** Workspace and project management. */
    WORKSPACE,

    /** Scheduling and automation. */
    SCHEDULING,

    /** Skill invocation. */
    SKILL,

    /** General-purpose tools. */
    GENERAL,

    /** External tools loaded from OpenAPI specs or other external sources. */
    EXTERNAL
}
