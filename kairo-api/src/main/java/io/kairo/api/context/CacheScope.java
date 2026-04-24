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
package io.kairo.api.context;

import io.kairo.api.Stable;

/**
 * Scope for prompt caching. Determines how long a system prompt segment is eligible for caching by
 * the model provider.
 */
@Stable(value = "Prompt cache scope enum; values frozen since v0.1", since = "1.0.0")
public enum CacheScope {
    /** Cached across all requests (rules, style, identity). */
    GLOBAL,
    /** Cached within a session (tools, skills, activated context). */
    SESSION,
    /** Recalculated per request (date, git status, MCP tools). */
    NONE
}
