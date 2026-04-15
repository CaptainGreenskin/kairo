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

/**
 * A named segment of the system prompt with a cache scope.
 *
 * <p>Multi-segment prompts allow model providers to cache different parts independently. For
 * example, Anthropic's API supports multiple text blocks with per-block {@code cache_control}
 * markers.
 *
 * @param name the segment name (e.g., "identity", "tools", "context")
 * @param content the segment text content
 * @param scope the cache scope for this segment
 */
public record SystemPromptSegment(String name, String content, CacheScope scope) {

    /** Whether this segment is eligible for caching. */
    public boolean isCacheable() {
        return scope != CacheScope.NONE;
    }

    /** Create a GLOBAL-scoped segment. */
    public static SystemPromptSegment global(String name, String content) {
        return new SystemPromptSegment(name, content, CacheScope.GLOBAL);
    }

    /** Create a SESSION-scoped segment. */
    public static SystemPromptSegment session(String name, String content) {
        return new SystemPromptSegment(name, content, CacheScope.SESSION);
    }

    /** Create a non-cacheable segment. */
    public static SystemPromptSegment dynamic(String name, String content) {
        return new SystemPromptSegment(name, content, CacheScope.NONE);
    }
}
